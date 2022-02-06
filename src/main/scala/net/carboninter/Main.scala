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

object Main extends ZIOAppDefault {

  implicit val _: Logger = LoggerFactory.getLogger(getClass)

  val tmpSubscribeMessagesStream =  ZStream.tick(5.seconds).drop(1) <&> ZStream(Vector("1.193792920"), Vector("1.193792920", "1.193791701"))

  val requestSinkAndResponseStream: ZIO[ZEnv & AppConfigService & BetfairConnection & BetfairStreamService, Throwable, (Sink[Throwable, RequestMessage, Nothing, Unit], ZStream[Clock & BetfairService, Throwable, ResponseMessage])] = for {
    streamService <- ZIO.service[BetfairStreamService]
    appConfigService <- ZIO.service[AppConfigService]
    socketDescriptor <- ZIO.service[BetfairConnection]
    counter <- Ref.make(0)
    (requestSink, responseStream) <- streamService.stream(socketDescriptor, counter)

    subFiber <- tmpSubscribeMessagesStream.mapZIO { marketIds =>
      for {
        id <- counter.getAndUpdate(_ + 1)
        config <- appConfigService.getAppConfig
      } yield buildSubscription(id, config.betfair.heartbeatRemote, marketIds)
    }.run(requestSink).fork

  } yield (requestSink, responseStream)



  def buildSubscription(id: Int, hb: Int, markets: Vector[String]) = {
    val marketFilter = MarketFilter(
      marketIds = Some(markets)
    )
    MarketSubscriptionMessage(
      id = Some(id),
      segmentationEnabled = None,
      clk = None,
      heartbeatMs = Some(hb),
      initialClk = None,
      marketFilter = Some(marketFilter),
      conflateMs = None,
      marketDataFilter = None
    )
  }

  val program = for {
    managedKafkaService   <- ZIO.service[ManagedKafkaService]

    (requestSink, responseStream) <- requestSinkAndResponseStream

    betfairResponsesHub   <- ZHub.unbounded[ResponseMessage]
    sink                   = ZSink.fromHub(betfairResponsesHub)

    kafkaChanges           = ZStream.fromHub(betfairResponsesHub)
    displayChanges         = ZStream.fromHub(betfairResponsesHub)
    displayHeartbeat       = ZStream.fromHub(betfairResponsesHub)

    _                     <- kafkaChanges.via(kafkaPublishMarketChanges).runDrain.fork
    _                     <- displayChanges.via(displayMarketChangeMessagePipeline).runDrain.fork
    _                     <- displayHeartbeat.via(displayHeartbeatCarrotPipeline).runDrain.fork

    betfairStreamFiber    <- responseStream.run(sink)  //Do this last after al subscriptions
  } yield ()


  private def errorHandler(throwable: Throwable): ZIO[LoggerAdapter, Nothing, Unit] =
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
    } yield ()

  def run: ZIO[Environment, Any, Any] = program.flatMapError(errorHandler)
    .provideSome[Environment](BetfairIdentityService.live,
      BetfairService.live,
      BetfairConnection.live,
      BetfairStreamService.live,
      LoggerAdapter.live,
      AppConfigService.live,
      ManagedKafkaService.live,
      MarketChangeRenderer.live
    )
}
