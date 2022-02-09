package net.carboninter.pipelines

import net.carboninter.appconf.AppConfigService
import net.carboninter.kafka.ManagedKafkaService
import net.carboninter.models.*
import swagger.definitions.*
import swagger.definitions.MarketChangeMessage.Ct.Heartbeat
import zio.{Clock, Ref, ZIO}
import zio.stream.{ZPipeline, ZStream}
import zio.Duration.*
import zio.*
import zio.metrics.MetricKey.Counter

object CommandPipelines {

  def subscriptionCommandsPipeline(heartbeatRemote: Int, counter: Ref[Int]): ZPipeline[Any, Throwable, Command, MarketSubscriptionMessage] =
    ZPipeline.collect {
      case subscribeCommand: SubscribeCommand => subscribeCommand
    } >>> ZPipeline.mapZIO { subscribeCommand =>
      for {
        i <- counter.getAndUpdate(_+1)
      } yield buildSubscription(i, heartbeatRemote, subscribeCommand.marketIds.toVector)
    }

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
}
