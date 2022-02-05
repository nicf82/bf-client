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
  def managedSocket: TaskManaged[SocketDescriptor]
  def stream(socketDescriptor: TaskManaged[SocketDescriptor], counter: Ref[Int]): UIO[(RequestMessage => UIO[Boolean], ZStream[Clock, Throwable, ResponseMessage])]

object BetfairStreamService:
  val live: URLayer[AppConfigService & LoggerAdapter & BetfairIdentityService, BetfairStreamService] =
    (LiveBetfairStreamService(_, _, _)).toLayer[BetfairStreamService]

case class LiveBetfairStreamService(appConfigService: AppConfigService, loggerAdapter: LoggerAdapter, betfairIdentityService: BetfairIdentityService) extends BetfairStreamService:

  implicit val logger: Logger = LoggerFactory.getLogger(getClass)
  val sslSocketFactory = SSLSocketFactory.getDefault

  def publisher(os: ZOutputStream)(message: RequestMessage): ZIO[Any, Throwable, Unit] = for {
    json  <- ZIO.succeed(message.asJson.noSpaces)
    _     <- loggerAdapter.debug("Publishing: " + json)
    bytes <- ZIO.succeed((json + "\r\n").getBytes)
    _     <- os.write(Chunk.fromArray(bytes))
  } yield ()


  val managedSocket: TaskManaged[SocketDescriptor] = {
    def acquire: ZIO[Any, Throwable, SocketDescriptor] = for {
      config <- appConfigService.getAppConfig.map(_.betfair)
      socket <- ZIO.attempt {
        val socket = sslSocketFactory.createSocket(config.streamApi.host, config.streamApi.port).asInstanceOf[SSLSocket]
        socket.startHandshake()
        socket.setReceiveBufferSize(1024 * 1000 * 2) //shaves about 20s off firehose image.
        socket.setSoTimeout(30*1000);
        socket
      }
    } yield SSLSocketDescriptor(socket)

    def release(s: SocketDescriptor): ZIO[Any, Nothing, Unit] = for {
      _ <- loggerAdapter.info("Closing betfair stream socket")
      _ <- s.close
    } yield ()

    ZManaged.acquireReleaseWith(acquire)(release)
  }

  override def stream(socketDescriptor: TaskManaged[SocketDescriptor], counter: Ref[Int]): UIO[(RequestMessage => UIO[Boolean], ZStream[Clock, Throwable, ResponseMessage])] = {

    for {
      publishQueue <- ZQueue.unbounded[RequestMessage]
      config <- appConfigService.getAppConfig.map(_.betfair)
    } yield {

      val responseStream = ZStream.managed(socketDescriptor)
        .mapZIO { socket =>
          val requests = ZStream.fromQueueWithShutdown(publishQueue)
          val heartbeat = if(config.heartbeatLocal > 0)
              ZStream.tick(config.heartbeatLocal.millis).drop(1).mapZIO(_ => counter.getAndUpdate(_ + 1).map(i => HeartbeatMessage(Some(i))))
            else
              ZStream.empty
          for {
            mergedStream <- (requests merge heartbeat).runForeach { m =>
              publisher(socket.outputStream)(m)
            }.fork
          } yield (counter, socket.inputStream)
        }.flatMap { case (counter, stream) =>
          stream.via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)
            .tap(m => loggerAdapter.debug("Received: " + m))
            .map(decode[ResponseMessage])
            .mapZIO(e => ZIO.fromEither(e))
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

              case msg@StatusMessage(id, Some(connectionsAvailable), errorMessage, errorCode, connectionId, Some(connectionClosed), Some(StatusCode.Success)) =>
                for {
                  _ <- loggerAdapter.info(s"Connected to stream api successfully, ${connectionsAvailable} connections available")
                } yield msg

              case msg@StatusMessage(id, connectionsAvailable, errorMessage, errorCode, connectionId, Some(connectionClosed), Some(StatusCode.Success)) =>
                for {
                  _ <- loggerAdapter.trace(s"Status update: Connection is " + (if (connectionClosed) "closed" else "open"))
                } yield msg

              case msg =>
                ZIO.succeed(msg)
              //          case msg@MarketChangeMessage(id, ct, clk, heartbeatMs, pt, initialClk, mc, conflateMs, segmentType, status) => msg
              //          case msg@OrderChangeMessage(id, ct, clk, heartbeatMs, pt, oc, initialClk, conflateMs, segmentType, status) => msg

            }
        }
      (publishQueue.offer, responseStream)
    }
  }
