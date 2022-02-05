package net.carboninter

import net.carboninter.services.*
import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import zio.stream.*
import zio.*
import cats.syntax.show.*
import net.carboninter.syntax.*
import net.carboninter.models.{LocalMarketCatalog, LocalRunner}

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

  val program: RIO[ZEnv & LoggerAdapter & AppConfigService & BetfairService & BetfairStreamService, Unit] = for {
    streamService <- ZIO.service[BetfairStreamService]
    betfairService <- ZIO.service[BetfairService]
    loggerAdapter <- ZIO.service[LoggerAdapter]
    appConfigService <- ZIO.service[AppConfigService]
    counter <- Ref.make(0)
    managedSocket = streamService.managedSocket
    (publish, responseStream) <- streamService.stream(managedSocket, counter)
    _ <- responseStream.mapZIO {
      //Authentication response
      case msg@StatusMessage(_, Some(i), None, None, _, _, Some(Success)) =>
        for {
          id <- counter.getAndUpdate(_ + 1)
          config <- appConfigService.getAppConfig
        } yield (msg, Some(buildSubscription(id, config.betfair.heartbeatRemote)))

      case msg@MarketChangeMessage(id, Some(Heartbeat), clk, heartbeatMs, pt, initialClk, mc, conflateMs, segmentType, status) =>
        print("🥕")
        ZIO.succeed( (msg, None) )

      case msg@MarketChangeMessage(id, ct, clk, heartbeatMs, pt, initialClk, mc, conflateMs, segmentType, status) => for {
        _ <- MarketChangeLogic.handleMarketChangeMessage(msg)
      } yield (msg, None)

      case msg =>
        ZIO.succeed( (msg, None) )
    }.tap {
      case (msg, Some(m)) =>
        publish(m) as msg
      case (msg, None) =>
        ZIO.succeed(msg)
    }.runDrain

  } yield ()

  def buildSubscription(id: Int, hb: Int) = {
    val marketFilter = MarketFilter(
//      countryCodes = Some(Vector("GB")),
//      eventTypeIds = Some(Vector("7")),        //Horseys
//      eventIds = Some(Vector("31212112")),     //Chepstow, Feb 4th
      marketIds = Some(Vector("1.194278823")),// "1.194193568")), //16:00, 16:35
//      bspMarket = Some(true)
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
    .provideSome[Environment](BetfairIdentityService.live, BetfairService.live, BetfairStreamService.live, LoggerAdapter.live, AppConfigService.live)
}
