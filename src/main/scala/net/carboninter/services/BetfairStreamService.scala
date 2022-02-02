package net.carboninter.services

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.Credentials
import org.slf4j.{Logger, LoggerFactory}
import swagger.definitions.StatusMessage.{ErrorCode, StatusCode}
import swagger.definitions.{AuthenticationMessage, ConnectionMessage, HeartbeatMessage, MarketChangeMessage, OrderChangeMessage, RequestMessage, ResponseMessage, StatusMessage}
import zio.{UIO, *}
import zio.Duration.*
import zio.stream.*

import java.io.{IOException, InputStream, OutputStream}
import java.net.Socket
import javax.net.SocketFactory
import javax.net.ssl.{SSLSocket, SSLSocketFactory}

trait BetfairStreamService:
  def stream: UIO[(Queue[RequestMessage], ZStream[Clock & Random, Throwable, ResponseMessage])]

object BetfairStreamService:
  val live: URLayer[AppConfigService & LoggerAdapter & BetfairIdentityService, BetfairStreamService] =
    (LiveBetfairStreamService(_, _, _)).toLayer[BetfairStreamService]

case class LiveBetfairStreamService(appConfigService: AppConfigService, loggerAdapter: LoggerAdapter, betfairIdentityService: BetfairIdentityService) extends BetfairStreamService:

  implicit val logger: Logger = LoggerFactory.getLogger(getClass)
  val sslSocketFactory = SSLSocketFactory.getDefault

  def publisher(os: OutputStream)(message: RequestMessage): ZIO[Any, Throwable, Unit] = for {
    json  <- ZIO.succeed(message.asJson.noSpaces)
    _     <- loggerAdapter.debug("Publishing: " + json)
    bytes <- ZIO.succeed((json + "\r\n").getBytes)
    _     <- ZIO.attemptBlocking(os.write(bytes))
  } yield ()


  override val stream: UIO[(Queue[RequestMessage], ZStream[Clock & Random, Throwable, ResponseMessage])] = {

    (ZQueue.unbounded[RequestMessage] <*> appConfigService.getAppConfig.map(_.betfair)) map { case (publishQueue, config) =>

      val managedSocket = ZManaged
        .acquireReleaseWith(ZIO.attempt(sslSocketFactory.createSocket(config.streamApi.host, config.streamApi.port))) { s =>
          for {
            _ <- loggerAdapter.info("Closing betfair stream socket")
            _ <- ZIO.succeed(s.getInputStream.close())
            _ <- ZIO.succeed(s.getOutputStream.close())
            _ <- ZIO.succeed(s.close())
          } yield ()
        }

      val responseStream = (ZStream.fromZIO(Ref.make(0)) <*> ZStream.managed(managedSocket))
        .mapZIO { case (counter, socket) =>
          val requests = ZStream.fromQueueWithShutdown(publishQueue)
          val heartbeat = ZStream.tick(15.seconds).drop(1).mapZIO(_ => counter.getAndUpdate(_ + 1).map(i => HeartbeatMessage(Some(i))))
          for {
            mergedStream <- (requests merge heartbeat).runForeach { m =>
              publisher(socket.getOutputStream)(m)
            }.fork
          } yield (counter, ZStream.fromInputStream(socket.getInputStream))
        }.flatMap { case (counter, stream) =>
          stream.via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)
            .tap(m => loggerAdapter.debug("Received: " + m))
            .map(decode[ResponseMessage])
            .mapZIO(e => ZIO.fromEither(e).mapError(_.getCause))
            .mapZIO {

              case msg@ConnectionMessage(id, connectionId) => for {
                creds <- betfairIdentityService.getCredentials
                id <- counter.getAndUpdate(_ + 1)
                m = AuthenticationMessage(Some(id), Some(creds.payload.token), Some(config.appKey))
                _ <- publishQueue.offer(m)
              } yield msg

              //All errors apart from SUBSCRIPTION_LIMIT_EXCEEDED close the connection
              case msg@StatusMessage(id, connectionsAvailable, errorMessage, errorCode, connectionId, Some(false), Some(StatusCode.Failure)) =>
                for {
                  _ <- loggerAdapter.warn(s"$errorCode: $errorMessage - (this does not terminate the connection)")
                } yield msg

              case msg@StatusMessage(id, connectionsAvailable, errorMessage, errorCode, connectionId, Some(true), Some(StatusCode.Failure)) =>
                for {
                  _ <- loggerAdapter.error(s"$errorCode: $errorMessage - (terminating the connection): ")
                } yield msg

              case msg@StatusMessage(id, connectionsAvailable, errorMessage, errorCode, connectionId, Some(connectionClosed), Some(StatusCode.Success)) =>
                for {
                  _ <- loggerAdapter.trace(s"Status update: Connection is " + (if (connectionClosed) "closed" else "open"))
                } yield msg

              case msg => ZIO.succeed(msg)
              //          case msg@MarketChangeMessage(id, ct, clk, heartbeatMs, pt, initialClk, mc, conflateMs, segmentType, status) => msg
              //          case msg@OrderChangeMessage(id, ct, clk, heartbeatMs, pt, oc, initialClk, conflateMs, segmentType, status) => msg

            }
        }
      (publishQueue, responseStream)
    }
  }
