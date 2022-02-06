package net.carboninter.services

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import net.carboninter.Main.buildSubscription
import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.*
import org.slf4j.{Logger, LoggerFactory}
import swagger.definitions.StatusMessage.StatusCode.Success
import swagger.definitions.StatusMessage.{ErrorCode, StatusCode}
import swagger.definitions.{AuthenticationMessage, ConnectionMessage, HeartbeatMessage, MarketChangeMessage, OrderChangeMessage, RequestMessage, ResponseMessage, StatusMessage}
import zio.{UIO, *}
import zio.Duration.*
import zio.stream.*

import java.io.{IOException, InputStream, OutputStream}
import java.net.Socket
import javax.net.SocketFactory
import javax.net.ssl.{SSLParameters, SSLSocket, SSLSocketFactory}

trait BetfairStreamService:
  def stream(socketDescriptor: SocketDescriptor, counter: Ref[Int]): UIO[(Sink[Throwable, RequestMessage, Nothing, Unit], ZStream[Clock, Throwable, ResponseMessage])]


object BetfairStreamService:
  val live: URLayer[AppConfigService & LoggerAdapter & BetfairIdentityService, BetfairStreamService] =
    (LiveBetfairStreamService(_, _, _)).toLayer[BetfairStreamService]


case class LiveBetfairStreamService(appConfigService: AppConfigService, loggerAdapter: LoggerAdapter, betfairIdentityService: BetfairIdentityService) extends BetfairStreamService:

  implicit val logger: Logger = LoggerFactory.getLogger(getClass)

  override def stream(socketDescriptor: SocketDescriptor, counter: Ref[Int]): UIO[(Sink[Throwable, RequestMessage, Nothing, Unit], ZStream[Clock, Throwable, ResponseMessage])] = {

    val requestSink: Sink[Throwable, RequestMessage, Nothing, Unit] = {
      socketDescriptor.requestSink.contramap(request => request.asJson.noSpaces)
    }

    for {
      config <- appConfigService.getAppConfig.map(_.betfair)
    } yield {

      val responseStream = ZStream.succeed(1)
        .flatMap { _ =>
          socketDescriptor.responseStream.via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)
            .tap(m => loggerAdapter.debug("Received: " + m))
            .map(decode[ResponseMessage])
            .mapZIO(e => ZIO.fromEither(e))
            .mapZIO[Clock, Throwable, (ResponseMessage, Option[RequestMessage])] {

              //Request authentication in response to connection
              case response@ConnectionMessage(id, connectionId) => for {
                creds <- betfairIdentityService.getCredentials
                id <- counter.getAndUpdate(_ + 1)
                request = AuthenticationMessage(Some(id), Some(creds.payload.token), Some(config.appKey))
              } yield (response, Some(request))

              //All errors apart from SUBSCRIPTION_LIMIT_EXCEEDED close the connection
              case response@StatusMessage(id, connectionsAvailable, errorMessage, errorCode, connectionId, Some(false), Some(StatusCode.Failure)) =>
                for {
                  _ <- loggerAdapter.warn(s"$errorCode: $errorMessage - (this does not terminate the connection)")
                } yield (response, None)

              case response@StatusMessage(id, connectionsAvailable, errorMessage, errorCode, connectionId, Some(true), Some(StatusCode.Failure)) =>
                for {
                  _ <- loggerAdapter.error(s"$errorCode: $errorMessage - (terminating the connection): ")
                } yield (response, None)

              case response@StatusMessage(id, Some(connectionsAvailable), errorMessage, errorCode, connectionId, Some(connectionClosed), Some(StatusCode.Success)) =>
                for {
                  _ <- loggerAdapter.info(s"Connected to stream api successfully, ${connectionsAvailable} connections available")
                } yield (response, None)

              case response@StatusMessage(id, connectionsAvailable, errorMessage, errorCode, connectionId, Some(connectionClosed), Some(StatusCode.Success)) =>
                for {
                  _ <- loggerAdapter.trace(s"Status update: Connection is " + (if (connectionClosed) "closed" else "open"))
                } yield (response, None)

              case msg =>
                ZIO.succeed( (msg, None) )
            }
            .tapSink(requestSink.contramap((m: (ResponseMessage, Option[RequestMessage])) => m._2.get).filterInput(_._2.isDefined), 16)
            .map {
              case (response, _) => response
            }

        }
      (requestSink, responseStream)
    }
  }

object ManagedSocket:

  private val sslSocketFactory = SSLSocketFactory.getDefault
  implicit val _: Logger = LoggerFactory.getLogger(getClass)

  val socket: RLayer[AppConfigService & LoggerAdapter, SocketDescriptor] = {
    def acquire: ZIO[AppConfigService & LoggerAdapter, Throwable, SocketDescriptor] = for {
      config <- ZIO.serviceWithZIO[AppConfigService](_.getAppConfig.map(_.betfair))
      socket <- ZIO.attempt {
        val socket = sslSocketFactory.createSocket(config.streamApi.host, config.streamApi.port).asInstanceOf[SSLSocket]
        socket.startHandshake()
        socket.setReceiveBufferSize(1024 * 1000 * 2) //shaves about 20s off firehose image.
        socket.setSoTimeout(30*1000);
        socket
      }
    } yield SSLSocketDescriptor(socket)

    def release(s: SocketDescriptor): ZIO[AppConfigService & LoggerAdapter, Nothing, Unit] = for {
      _ <- ZIO.serviceWithZIO[LoggerAdapter](_.info("Closing betfair stream socket"))
      _ <- s.close
    } yield ()

    ZManaged.acquireReleaseWith(acquire)(release)
  }.toLayer