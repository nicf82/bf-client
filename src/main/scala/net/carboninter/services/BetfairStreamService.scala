package net.carboninter.services

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.Credentials
import org.slf4j.{Logger, LoggerFactory}
import swagger.definitions.StatusMessage.StatusCode
import swagger.definitions.{AuthenticationMessage, ConnectionMessage, MarketChangeMessage, OrderChangeMessage, RequestMessage, ResponseMessage, StatusMessage}
import zio.*
import zio.stream.*

import java.io.{IOException, InputStream, OutputStream}
import java.net.Socket
import javax.net.SocketFactory
import javax.net.ssl.{SSLSocket, SSLSocketFactory}

trait BetfairStreamService:
  def stream: ZStream[Any, Throwable, ResponseMessage]

object BetfairStreamService:
  val live: URLayer[AppConfigService & LoggerAdapter & BetfairIdentityService, BetfairStreamService] =
    (LiveBetfairStreamService(_, _, _)).toLayer[BetfairStreamService]

case class LiveBetfairStreamService(appConfigService: AppConfigService, loggerAdapter: LoggerAdapter, betfairIdentityService: BetfairIdentityService) extends BetfairStreamService:

  implicit val logger: Logger = LoggerFactory.getLogger(getClass)
  val factory = SSLSocketFactory.getDefault

  def openSocket: Task[Socket] = for {
    socket <- ZIO.attempt(factory.createSocket("stream-api.betfair.com", 443))
  } yield socket

  def managed(is: InputStream): ZManaged[Any, IOException, InputStream] =
    ZManaged.fromAutoCloseable(
      ZIO.attempt(is)
    ).refineToOrDie[IOException]


  def publisher(os: OutputStream)(message: RequestMessage) = for {
    json  <- ZIO.succeed(message.asJson.noSpaces)
    _     <- loggerAdapter.debug("Publishing: " + json)
    bytes <- ZIO.succeed((json + "\r\n").getBytes)
    _     <- ZIO.attemptBlocking(os.write(bytes))
  } yield ()


  val stream0: ZStream[Any, Throwable, ResponseMessage] = ZStream.unwrap(for {
    config <- appConfigService.getAppConfig.map(_.betfair)
    socket <- openSocket

    outputStream <- ZIO.attempt(socket.getOutputStream)
    publish = publisher(outputStream)

    inStream = ZStream
      .fromInputStreamManaged(managed(socket.getInputStream))
      .via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)
      .tap(loggerAdapter.debug)
      .map(decode[ResponseMessage])
      .mapZIO(e => ZIO.fromEither(e).mapError(_.getCause))
      .mapZIO {
        case msg@ConnectionMessage(id, connectionId) => for {
          creds <- betfairIdentityService.getCredentials
          _     <- loggerAdapter.info("Connection message: " + msg)
          m      = AuthenticationMessage(Some(1), Some(creds.payload.token), Some(config.appKey))
          _     <- publish(m)

        } yield msg
        case msg@StatusMessage(id, connectionsAvailable, errorMessage, errorCode, connectionId, Some(connectionClosed), Some(StatusCode.Failure)) => for {
            _     <- loggerAdapter.error(s"Terminated connection? $connectionClosed because: $errorCode " + errorMessage)
          } yield msg
        case msg@StatusMessage(id, connectionsAvailable, errorMessage, errorCode, connectionId, connectionClosed, Some(StatusCode.Success)) => for {
            _     <- loggerAdapter.info(s"Status update: $errorCode " + errorMessage)
          } yield msg
        case msg => ZIO.succeed(msg)
//          case msg@MarketChangeMessage(id, ct, clk, heartbeatMs, pt, initialClk, mc, conflateMs, segmentType, status) => msg
//          case msg@OrderChangeMessage(id, ct, clk, heartbeatMs, pt, oc, initialClk, conflateMs, segmentType, status) => msg

      }
  } yield inStream)


  //TODO - get some kind of managed socket working
  val stream1: ZStream[Any, Throwable, ResponseMessage] = ZStream.unwrap(for {
    config <- appConfigService.getAppConfig.map(_.betfair)
    //    socket <- openSocket

    managedSocket = ZManaged
      .acquireReleaseWith(ZIO.attempt(factory.createSocket("stream-api.betfair.com", 443))) { s =>
        for {
          _ <- loggerAdapter.warn("Closing socket now")
          _ <- ZIO.succeed(s.getInputStream.close())
          _ <- ZIO.succeed(s.getOutputStream.close())
          _ <- ZIO.succeed(s.close())
        } yield ()
      }

    inStream <- for {
      _ <- loggerAdapter.trace("here")
      inStream = ZStream
        .managed(managedSocket)
        .map { socket =>
          (publisher(socket.getOutputStream), ZStream.fromInputStream(socket.getInputStream))
        }.flatMap { case (publish, stream) =>
          stream
            .via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)
            .tap(loggerAdapter.debug)
            .map(decode[ResponseMessage])
            .mapZIO(e => ZIO.fromEither(e).mapError(_.getCause))
            .mapZIO {
              case msg@ConnectionMessage(id, connectionId) => for {
                creds <- betfairIdentityService.getCredentials
                _     <- loggerAdapter.info("Connection message: " + msg)
                m      = AuthenticationMessage(Some(1), Some(creds.payload.token), Some(config.appKey))
                _     <- publish(m)
              } yield msg
              case msg@StatusMessage(id, connectionsAvailable, errorMessage, errorCode, connectionId, Some(connectionClosed), Some(StatusCode.Failure)) => for {
                _     <- loggerAdapter.error(s"Terminated connection? $connectionClosed because: $errorCode " + errorMessage)
              } yield msg
              case msg@StatusMessage(id, connectionsAvailable, errorMessage, errorCode, connectionId, Some(connectionClosed), Some(StatusCode.Success)) => for {
                _     <- loggerAdapter.info(s"Status update: Connection is " + (if(connectionClosed) "closed" else "open"))
              } yield msg
              case msg => ZIO.succeed(msg)
              //          case msg@MarketChangeMessage(id, ct, clk, heartbeatMs, pt, initialClk, mc, conflateMs, segmentType, status) => msg
              //          case msg@OrderChangeMessage(id, ct, clk, heartbeatMs, pt, oc, initialClk, conflateMs, segmentType, status) => msg

            }
        }
    } yield inStream
  } yield inStream)

  override val stream = stream1
