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
      countryCodes = Some(Vector("GB")),
      eventTypeIds = Some(Vector("7")), //Horseys
      eventIds = Some(Vector("31214001")), //Horseys
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
