package net.carboninter.pipelines

import net.carboninter.Main.getClass
import net.carboninter.appconf.AppConfigService
import net.carboninter.betfair.*
import net.carboninter.kafka.ManagedKafkaService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.*
import org.slf4j.{Logger, LoggerFactory}
import swagger.definitions.*
import swagger.definitions.MarketChangeMessage.Ct.Heartbeat
import zio.{Clock, Ref, ZIO}
import zio.stream.{ZPipeline, ZStream}
import zio.Duration.*
import zio.*
import zio.metrics.MetricKey.Counter

object CommandPipelines {

  implicit val _: Logger = LoggerFactory.getLogger(getClass)

  def subscriptionCommandsPipeline(heartbeatRemote: Int, counter: BetfairStreamCounterRef): ZPipeline[LoggerAdapter, Throwable, Command, MarketSubscriptionMessage] =
    ZPipeline.collect[Command, SubscribeCommand] {
      case subscribeCommand: SubscribeCommand => subscribeCommand
    } >>> ZPipeline.mapZIO { (subscribeCommand: SubscribeCommand) =>
      for {
        _ <- LoggerAdapter.info("Subscribing to markets: " + subscribeCommand.marketIds.mkString(", "))
        i <- counter.getAndUpdate(_+1)
      } yield buildSubscription(i, heartbeatRemote, subscribeCommand.marketIds.toVector)
    }

  def buildSubscription(id: Int, hb: Int, markets: Vector[String]) = {
    val marketFilter = MarketFilter(
      marketIds = Some(markets)
    )
    MarketSubscriptionMessage(
      id = Some(id),
      // https://docs.developer.betfair.com/display/1smk3cen4v3lu3yomq5qye0ni/Exchange+Stream+API#ExchangeStreamAPI-ChangeMessageSegmentation
      segmentationEnabled = Some(false),  //May improve performance to enable this but it'll need to be handled per message
      clk = None,
      heartbeatMs = Some(hb),
      initialClk = None,
      marketFilter = Some(marketFilter),
      conflateMs = None,
      marketDataFilter = None
    )
  }
}
