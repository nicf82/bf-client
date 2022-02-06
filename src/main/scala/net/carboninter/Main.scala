package net.carboninter

import net.carboninter.services.{BetfairStreamService, *}
import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import zio.stream.*
import zio.*
import cats.syntax.show.*
import net.carboninter.syntax.*
import net.carboninter.models.*

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

  val tmpSubscribeMessagesStream =  ZStream.tick(10.seconds).drop(1) <&> ZStream(Vector("1.193792920"), Vector("1.193792920", "1.193791701"))

  val betfairStream: ZIO[ZEnv & LoggerAdapter & AppConfigService & BetfairService & SocketDescriptor & BetfairStreamService & MarketChangePublisher, Throwable, ZStream[Clock & BetfairService, Throwable, ResponseMessage]] = for {
    streamService <- ZIO.service[BetfairStreamService]
    betfairService <- ZIO.service[BetfairService]
    loggerAdapter <- ZIO.service[LoggerAdapter]
    appConfigService <- ZIO.service[AppConfigService]
    marketChangePublisher <- ZIO.service[MarketChangePublisher]
    socketDescriptor <- ZIO.service[SocketDescriptor]
    counter <- Ref.make(0)
    (requestSink, responseStream) <- streamService.stream(socketDescriptor, counter)

    subFiber <- tmpSubscribeMessagesStream.mapZIO { marketIds =>
      for {
        id <- counter.getAndUpdate(_ + 1)
        _ = println("Subscribing to : " + marketIds)
        config <- appConfigService.getAppConfig
      } yield buildSubscription(id, config.betfair.heartbeatRemote, marketIds)
    }.run(requestSink).fork

    primaryStream = responseStream.mapZIO {

      case msg@MarketChangeMessage(id, Some(Heartbeat), clk, heartbeatMs, pt, initialClk, mc, conflateMs, segmentType, status) =>
        print("🥕")
        ZIO.succeed( msg )

      case msg@MarketChangeMessage(id, ct, clk, heartbeatMs, pt, initialClk, mc, conflateMs, segmentType, status) => for {
        _ <- marketChangePublisher.handleMarketChangeMessage(msg)
      } yield msg

      case msg =>
        ZIO.succeed( msg )
    }

  } yield primaryStream



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
    _ <- ZStream.unwrap(betfairStream).runDrain
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
      ManagedSocket.socket,
      BetfairStreamService.live,
      LoggerAdapter.live,
      AppConfigService.live,
      ManagedKafka.producer,
      MarketChangePublisher.live
    )
}
