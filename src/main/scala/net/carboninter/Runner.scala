package net.carboninter

import net.carboninter.services.{BetfairIdentityService, BetfairStreamService}
import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import zio.stream.*
import zio.*
import cats.syntax.show.*

import java.util.concurrent.TimeUnit
import org.slf4j.{Logger, LoggerFactory}
import swagger.definitions.AllRequestTypesExample.OpTypes.members.MarketSubscription
import swagger.definitions.*
import swagger.definitions.StatusMessage.StatusCode.*
import swagger.definitions.MarketChangeMessage.Ct.*
import scala.{Console => C}
import java.time.Instant

object Runner extends ZIOAppDefault {

  implicit val _: Logger = LoggerFactory.getLogger(getClass)

  val program: ZIO[ZEnv & LoggerAdapter & AppConfigService & BetfairStreamService, Throwable, Unit] = for {
    streamService <- ZIO.service[BetfairStreamService]
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
        print(".")
        ZIO.succeed( (msg, None) )

      case msg@MarketChangeMessage(id, ct, clk, heartbeatMs, pt, initialClk, mc, conflateMs, segmentType, status) =>
        outputMarketInfo(msg)
        ZIO.succeed( (msg, None) )

      case msg =>
        ZIO.succeed( (msg, None) )
    }.tap {
      case (msg, Some(m)) =>
        publish(m) as msg
      case (msg, None) =>
        ZIO.succeed(msg)
    }.runDrain

  } yield ()

  def cout(ansi: String*)(s: String) = println(ansi.mkString + s + C.RESET)

  def outputMarketInfo(mcm: MarketChangeMessage) = {
    cout(C.BOLD, C.BLUE)(s"\npt: ${Instant.ofEpochMilli(mcm.pt.get)}, ct: ${mcm.ct}")
    cout(C.BOLD, C.BLUE)("======================================")

    for(mc <- mcm.mc.getOrElse(Nil)) {
      cout(C.BOLD, C.GREEN)(s"${mc.id.get} ${mc.marketDefinition.get.venue.get} ${mc.marketDefinition.get.marketTime.get} ${mc.marketDefinition.get.bettingType.get} ${mc.marketDefinition.get.marketType.get}")
      cout(C.BOLD, C.GREEN)("--------------------------------------")
      for((rc, i) <- mc.rc.getOrElse(Nil).zipWithIndex) {
        cout(C.BOLD, C.YELLOW)(s"\n$i, ${rc.id}")
        for(atb <- rc.atb.getOrElse(Nil).sortBy(_(0))) {
          print(" " + atb(0) + "@" + atb(1))
        }
        println()
      }
    }
  }

  def buildSubscription(id: Int, hb: Int) = {
    val marketFilter = MarketFilter(
      countryCodes = Some(Vector("GB")),
      eventTypeIds = Some(Vector("7")),        //Horseys
      eventIds = Some(Vector("31212112")),     //Chepstow, Feb 4th
      marketIds = Some(Vector("1.194193563")),// "1.194193568")), //16:00, 16:35
      bspMarket = Some(true)
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
    .provideSome[Environment](BetfairIdentityService.live, BetfairStreamService.live, LoggerAdapter.live, AppConfigService.live)
}
