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

import java.io.{BufferedInputStream, InputStream}
import java.lang.RuntimeException
import scala.Console as C
import java.time.Instant
import scala.jdk.CollectionConverters.*

object Main extends ZIOAppDefault {

  implicit val _: Logger = LoggerFactory.getLogger(getClass)

  val betfairCounterReqSinkAndRespStream: ZIO[AppConfigService & BetfairConnection & BetfairStreamService, Throwable, (BetfairStreamCounterRef, Sink[Throwable, RequestMessage, Nothing, Unit], ZStream[Any, Throwable, ResponseMessage])] = for {
    streamService <- ZIO.service[BetfairStreamService]
    socketDescriptor <- ZIO.service[BetfairConnection]
    crr <- streamService.open(socketDescriptor)
  } yield crr


  val program = for {
    streamService                     <- ZIO.service[BetfairStreamService]
    managedKafkaService               <- ZIO.service[ManagedKafkaService]
    appConfig                         <- ZIO.serviceWithZIO[AppConfigService](_.getAppConfig)
    lastRemoteHBAt                    <- Ref.make(Instant.EPOCH)

    t                                      <- betfairCounterReqSinkAndRespStream
    (counter, requestSink, responseStream)  = t

    betfairResponsesHub               <- Hub.unbounded[ResponseMessage]
    betfairResponsesHubSink            = ZSink.fromHub(betfairResponsesHub)

    kafkaChanges                       = Stream.fromHub(betfairResponsesHub)
    displayHeartbeat                   = Stream.fromHub(betfairResponsesHub)

    _                                 <- kafkaChanges.via(collectMarketChangeMessages)
                                           .run(managedKafkaService.marketChangeMessageDeltaTopicSink).fork

    _                                 <- displayHeartbeat
                                           .via(updateLastRemoteHeartbeatPipeline(lastRemoteHBAt))
                                           //.via(displayHeartbeatCarrotPipeline)
                                           .runDrain.fork

    //Send local heartbeat if we have not seen a remote heartbeat in the last 10 seconds
    localHeartbeat                     = streamService.localHeartbeatStream(lastRemoteHBAt, counter)

    t                                 <- managedKafkaService.splitStreams
    (commandStream, mcmDeltasStream)   = t

    _                                 <- (localHeartbeat merge commandStream.via(subscriptionCommandsPipeline(appConfig.betfair.heartbeatRemote, counter)))
                                           .run(requestSink).fork

    _                                 <- mcmDeltasStream
//                                           .via(extractMarketChangeEnvelopesPipeline)
                                           .viaFunction(extractMarketChangeEnvelopesFunction)
                                           .viaFunction(hydrateMarketChangeFromCacheFunction)
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

  val mcmCache: ULayer[MarketChangeCache] = ZLayer.fromZIO(Ref.make(Map.empty[String, MarketChange]))

  override def run = program.flatMapError(errorHandler)
    .provide(BetfairIdentityService.live,
      BetfairConnection.live,
      BetfairStreamService.live,
      LoggerAdapter.live,
      AppConfigService.live,
      ManagedKafkaService.live,
      MarketChangeRenderer.live,
      mcmCache
    )
}
