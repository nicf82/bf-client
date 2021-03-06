package net.carboninter.betfair

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.*
import org.slf4j.{Logger, LoggerFactory}
import swagger.definitions.StatusMessage.StatusCode.Success
import swagger.definitions.StatusMessage.{ErrorCode, StatusCode}
import swagger.definitions.{AuthenticationMessage, ConnectionMessage, HeartbeatMessage, MarketChangeMessage, MarketSubscriptionMessage, OrderChangeMessage, OrderSubscriptionMessage, RequestMessage, ResponseMessage, StatusMessage}
import zio.{UIO, *}
import zio.Duration.*
import zio.stream.*

import java.io.{IOException, InputStream, OutputStream}
import java.net.Socket
import java.time.Instant
import javax.net.SocketFactory
import javax.net.ssl.{SSLParameters, SSLSocket, SSLSocketFactory}

trait BetfairStreamService:
  def open(socketDescriptor: BetfairConnection): UIO[(BetfairStreamCounterRef, Sink[Throwable, RequestMessage, Nothing, Unit], ZStream[Any, Throwable, ResponseMessage])]
  def localHeartbeatStream(lastRemoteHBAt: Ref[Instant], counter: BetfairStreamCounterRef): ZStream[Any, Nothing, HeartbeatMessage]


object BetfairStreamService:
  val live: URLayer[AppConfigService & LoggerAdapter & BetfairIdentityService, BetfairStreamService] =
    ZLayer.fromZIO {
      for {
        appConfigService <- ZIO.service[AppConfigService]
        loggerAdapter <- ZIO.service[LoggerAdapter]
        betfairIdentityService <- ZIO.service[BetfairIdentityService]
      } yield LiveBetfairStreamService(appConfigService, loggerAdapter, betfairIdentityService)
    }


case class LiveBetfairStreamService(appConfigService: AppConfigService, loggerAdapter: LoggerAdapter, betfairIdentityService: BetfairIdentityService) extends BetfairStreamService:

  implicit val logger: Logger = LoggerFactory.getLogger(getClass)

  override def open(socketDescriptor: BetfairConnection): UIO[(BetfairStreamCounterRef, Sink[Throwable, RequestMessage, Nothing, Unit], ZStream[Any, Throwable, ResponseMessage])] = {

    for {
      config <- appConfigService.getAppConfig.map(_.betfair)
      counter <- Ref.make(0)
      isAuthenticated <- Ref.make(false)
    } yield {

      val requestSink: Sink[Throwable, RequestMessage, Nothing, Unit] = {
        socketDescriptor.requestSink.contramapChunksZIO { (requests: Chunk[RequestMessage]) =>
          for {
            isAuthenticated <- isAuthenticated.get
            optStrings = for {
              request <- requests
            } yield if(request.isInstanceOf[AuthenticationMessage] || isAuthenticated) Some(request.asJson.noSpaces) else None
            validRequests = optStrings.flatten
            _ <- ZIO.foreach(validRequests)(request => loggerAdapter.debug("Sending: " + request))
          } yield validRequests
        }
      }

      val responseStream = ZStream.succeed(1)
        .flatMap { _ =>
          socketDescriptor.responseStream.via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)
            .tap(m => loggerAdapter.debug("Received: " + m))
            .map(decode[ResponseMessage])
            .mapZIO(e => ZIO.fromEither(e))
            .mapZIO[Any, Throwable, (ResponseMessage, Option[RequestMessage])] {

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

              //FIXME - according to docs, I need ot discard MarketSubscriptionMessages with ids other than the one returned here as they are from another (earlier) subscription
              case response@StatusMessage(id, Some(connectionsAvailable), errorMessage, errorCode, connectionId, Some(connectionClosed), Some(StatusCode.Success)) =>
                for {
                  _ <- isAuthenticated.update(_ => true)
                  _ <- loggerAdapter.info(s"Connected to stream api successfully, ${connectionsAvailable} connections available")
                } yield (response, None)

              case response@StatusMessage(id, connectionsAvailable, errorMessage, errorCode, connectionId, Some(connectionClosed), Some(StatusCode.Success)) =>
                for {
                  _ <- loggerAdapter.trace(s"Status update: Connection is " + (if (connectionClosed) "closed" else "open"))
                } yield (response, None)

              case msg =>
                ZIO.succeed( (msg, None) )
            }
            .tapSink(requestSink.contramap((m: (ResponseMessage, Option[RequestMessage])) => m._2.get).filterInput(_._2.isDefined))
            .map {
              case (response, _) => response
            }

        }

      (counter, requestSink, responseStream)
    }
  }

  override def localHeartbeatStream(lastRemoteHBAt: Ref[Instant], counter: BetfairStreamCounterRef): ZStream[Any, Nothing, HeartbeatMessage] =
    ZStream.tick(10.seconds).drop(1).filterZIO { _ =>
      for {
        last <- lastRemoteHBAt.get
        now <- Clock.instant
      } yield now.minusSeconds(10).isAfter(last)
    }.mapZIO { _ =>
      for {
        i <- counter.getAndUpdate(_+1)
      } yield HeartbeatMessage(Some(i))
    }
