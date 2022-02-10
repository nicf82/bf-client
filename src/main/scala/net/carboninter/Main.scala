package net.carboninter

import net.carboninter.betfair.*
import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import zio.stream.*
import zio.*
import cats.syntax.show.*
import net.carboninter.kafka.ManagedKafkaService
import net.carboninter.syntax.*
import net.carboninter.models.*
import net.carboninter.pipelines.Pipelines.*
import net.carboninter.pipelines.CommandPipelines.*
import net.carboninter.rendering.MarketChangeRenderer

import java.util.concurrent.TimeUnit
import org.slf4j.{Logger, LoggerFactory}
import swagger.definitions.AllRequestTypesExample.OpTypes.members.MarketSubscription
import swagger.definitions.*
import swagger.definitions.StatusMessage.StatusCode.*
import swagger.definitions.MarketChangeMessage.Ct.*

import java.lang.RuntimeException
import scala.Console as C
import java.time.Instant
import scala.jdk.CollectionConverters._

object Main extends ZIOAppDefault {

  implicit val _: Logger = LoggerFactory.getLogger(getClass)

  val betfairCounterReqSinkAndRespStream: ZIO[ZEnv & AppConfigService & BetfairConnection & BetfairStreamService, Throwable, (BetfairStreamCounterRef, Sink[Throwable, RequestMessage, Nothing, Unit], ZStream[Clock, Throwable, ResponseMessage])] = for {
    streamService <- ZIO.service[BetfairStreamService]
    socketDescriptor <- ZIO.service[BetfairConnection]
    crr <- streamService.open(socketDescriptor)
  } yield crr

  //Send local heartbeat if we have not seen a remote heartbeat in the last 10 seconds
  def localHeartbeatStream(lastRemoteHBAt: Ref[Instant], counter: BetfairStreamCounterRef) = ZStream.tick(10.seconds).drop(1).filterZIO { _ =>
    for {
      last <- lastRemoteHBAt.get
      now <- ZIO.serviceWithZIO[Clock](_.instant)
    } yield now.minusSeconds(10).isAfter(last)
  }.mapZIO { _ =>
    for {
      i <- counter.getAndUpdate(_+1)
    } yield HeartbeatMessage(Some(i))
  }

  val program = for {
    managedKafkaService               <- ZIO.service[ManagedKafkaService]
    appConfig                         <- ZIO.serviceWithZIO[AppConfigService](_.getAppConfig)
    lastRemoteHBAt                    <- Ref.make(Instant.EPOCH)

    (counter, requestSink, responseStream)
                                      <- betfairCounterReqSinkAndRespStream

    betfairResponsesHub               <- ZHub.unbounded[ResponseMessage]
    betfairResponsesHubSink            = ZSink.fromHub(betfairResponsesHub)

    kafkaChanges                       = ZStream.fromHub(betfairResponsesHub)
    displayHeartbeat                   = ZStream.fromHub(betfairResponsesHub)

    _                                 <- kafkaChanges.via(collectMarketChangeMessages)
                                           .run(managedKafkaService.marketChangeMessageDeltaTopicSink).fork

    _                                 <- displayHeartbeat
                                           .via(updateLastRemoteHeartbeatPipeline(lastRemoteHBAt))
                                           //.via(displayHeartbeatCarrotPipeline)
                                           .runDrain.fork

    localHeartbeat                     = localHeartbeatStream(lastRemoteHBAt, counter)

    (commandStream, mcmDeltasStream)
                                      <- managedKafkaService.splitStreams

    _                                 <- (localHeartbeat merge commandStream.via(subscriptionCommandsPipeline(appConfig.betfair.heartbeatRemote, counter)))
                                           .run(requestSink).fork

    _                                 <- mcmDeltasStream
                                           .via(extractMarketChangeEnvelopesPipeline)
                                           .via(hydrateMarketChangeFromCache)
                                           .via(displayMarketChangePipeline)
                                           .run(managedKafkaService.marketChangeTopicSink).fork

    betfairStreamFiber                <- responseStream.run(betfairResponsesHubSink)  //Do this last after all subscriptions
  } yield ()


  def errorHandler(throwable: Throwable): ZIO[LoggerAdapter, Nothing, Throwable] =
    for {
      loggerAdapter <- ZIO.service[LoggerAdapter]
      _ <- throwable match {
        case error: io.circe.Error =>
          loggerAdapter.error(error.getClass.toString + ": " + error.show, error)
        case t if t.getCause == io.circe.Error =>
          loggerAdapter.error(t.getClass.toString + ": " + t.getCause.asInstanceOf[io.circe.Error].show, t)
        case t =>
          loggerAdapter.error(t.getClass.toString + ": " + t.getMessage, t)
      }
    } yield throwable

  val mcmCache: ULayer[MarketChangeCache] = Ref.make(Map.empty[String, MarketChange]).toLayer

  def run: ZIO[Environment, Any, Any] = program.flatMapError(errorHandler)
    .provideSome[Environment](BetfairIdentityService.live,
      BetfairConnection.live,
      BetfairStreamService.live,
      LoggerAdapter.live,
      AppConfigService.live,
      ManagedKafkaService.live,
      MarketChangeRenderer.live,
      mcmCache
    )
}
