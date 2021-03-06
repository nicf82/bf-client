package net.carboninter.pipelines

import net.carboninter.Main.getClass
import net.carboninter.kafka.ManagedKafkaService
import net.carboninter.rendering.MarketChangeRenderer
import net.carboninter.betfair.*
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.{MarketChangeEnvelope, MarketChangeMergeTools}
import org.slf4j.*
import swagger.definitions.MarketChangeMessage.Ct
import swagger.definitions.MarketChangeMessage.Ct.Heartbeat
import swagger.definitions.{MarketChange, MarketChangeMessage, ResponseMessage}
import zio.*
import zio.stream.*

import java.time.Instant

object Pipelines:

  import MarketChangeMergeTools._
  implicit val _: Logger = LoggerFactory.getLogger(getClass)

  val collectMarketChangeMessages: ZPipeline[LoggerAdapter, Throwable, ResponseMessage, MarketChangeMessage] =
    ZPipeline.collect {
      case msg: MarketChangeMessage if msg.ct != Some(Heartbeat) =>
        msg
    }


  def extractMarketChangeEnvelopesFunction(s: UStream[MarketChangeMessage]) =
    s.flatMap { marketChangeMessage =>
      ZStream(marketChangeMessage) <*> ZStream.fromIterable(marketChangeMessage.mc.getOrElse(Nil).toList)
    }.map { case (marketChangeMessage, marketChange) =>
      MarketChangeEnvelope(marketChange, marketChangeMessage.ct != Some(Ct.SubImage))
    }


  def hydrateMarketChangeFromCacheFunction(s: UStream[MarketChangeEnvelope]): ZStream[LoggerAdapter with MarketChangeCache, Nothing, MarketChangeEnvelope] =
    s.mapZIO { marketChangeEnvelope =>
      for {
        cache         <- ZIO.service[MarketChangeCache]
        marketChange   = marketChangeEnvelope.marketChange
        _ <- ZIO.when(marketChange.img.getOrElse(false))(LoggerAdapter.warn("marketChange.img was set - this was not a delta, but it should now be handled ok in mergeMC"))
        mcNew        <- ZIO.ifZIO(ZIO.succeed(marketChangeEnvelope.isDelta))(
          cache.updateAndGet(_.updatedWith(marketChange.id)(original => original.map(mergeMC(_, marketChange)))),  //Deltas are merged into cache//A SubImage replaces the cache
          cache.updateAndGet(_.updated(marketChange.id, marketChange))                                             //A SubImage replaces the cache
        )
      } yield marketChangeEnvelope.withMarketChange(mcNew(marketChange.id))
    }

  val displayMarketChangePipeline: ZPipeline[MarketChangeRenderer & LoggerAdapter, Throwable, MarketChangeEnvelope, MarketChangeEnvelope] =
    ZPipeline.mapZIO { marketChangeEnvelope =>
      for {
        marketChangePublisher <- ZIO.service[MarketChangeRenderer]
//        _ <- marketChangePublisher.renderMarketChange(marketChangeEnvelope)
      } yield marketChangeEnvelope
    }

  val displayHeartbeatCarrotPipeline: ZPipeline[Any, Throwable, ResponseMessage, ResponseMessage] = ZPipeline.mapZIO { message =>
    for {
      _ <- message match {
        case msg@MarketChangeMessage(id, Some(Heartbeat), clk, heartbeatMs, pt, initialClk, mc, conflateMs, segmentType, status) =>
          Console.print("????")
        case _ => ZIO.unit
      }
    } yield message
  }

  def updateLastRemoteHeartbeatPipeline(lastHeartbeat: Ref[Instant]): ZPipeline[Any, Throwable, ResponseMessage, ResponseMessage] =
    ZPipeline.collect {
      case message: MarketChangeMessage if message.ct == Some(Heartbeat) => message
    } >>> ZPipeline.mapZIO { (message: MarketChangeMessage) =>
      for {
        now <- Clock.instant
        _ <- lastHeartbeat.update(_ => now) 
      } yield message
    }
