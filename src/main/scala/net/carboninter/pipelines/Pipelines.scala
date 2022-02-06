package net.carboninter.pipelines

import net.carboninter.kafka.ManagedKafkaService
import net.carboninter.services.*
import swagger.definitions.MarketChangeMessage.Ct.Heartbeat
import swagger.definitions.{MarketChange, MarketChangeMessage, ResponseMessage}
import zio.*
import zio.stream.*

object Pipelines:
  
  val flatMapMarketChangesPipeline: ZPipeline[Any, Nothing, ResponseMessage, MarketChange] =
    ZPipeline.collect {
      case message: MarketChangeMessage if message.ct != Some(Heartbeat) => message
    } >>> ZPipeline.mapChunks { responseMessages =>
      for {
        message <- responseMessages
        marketChange <- message.mc.getOrElse(Nil).toList
      } yield marketChange
    }

  val kafkaPublishMarketChanges: ZPipeline[ManagedKafkaService, Throwable, ResponseMessage, MarketChange] =
    flatMapMarketChangesPipeline >>> ZPipeline.mapZIO { marketChange =>
      for {
        managedKafkaService <- ZIO.service[ManagedKafkaService]
        _ <- managedKafkaService.publishMarketChange(marketChange)
      } yield marketChange
    }

  val displayMarketChangeMessagePipeline: ZPipeline[Clock & BetfairService & MarketChangeRenderer, Throwable, ResponseMessage, Unit] =
    ZPipeline.collect {
      case message: MarketChangeMessage if message.ct != Some(Heartbeat) => message
    } >>> ZPipeline.mapZIO { message =>
      for {
        marketChangePublisher <- ZIO.service[MarketChangeRenderer]
        _ <- marketChangePublisher.renderMarketChangeMessage(message)
      } yield ()
    }

  val displayHeartbeatCarrotPipeline: ZPipeline[Console, Throwable, ResponseMessage, Unit] = ZPipeline.mapZIO { message =>
    for {
      _ <- message match {
        case msg@MarketChangeMessage(id, Some(Heartbeat), clk, heartbeatMs, pt, initialClk, mc, conflateMs, segmentType, status) =>
          Console.print("🥕")
        case _ => ZIO.unit
      }
    } yield ()
  }
