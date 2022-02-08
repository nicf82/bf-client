package net.carboninter.pipelines

import net.carboninter.kafka.ManagedKafkaService
import net.carboninter.rendering.MarketChangeRenderer
import net.carboninter.betfair.*
import swagger.definitions.MarketChangeMessage.Ct.Heartbeat
import swagger.definitions.{MarketChange, MarketChangeMessage, ResponseMessage}
import zio.*
import zio.stream.*

import java.time.Instant

object Pipelines:

//  val flatMapMarketChangesPipeline: ZPipeline[Any, Nothing, ResponseMessage, MarketChange] =
//    ZPipeline.collect {
//      case message: MarketChangeMessage if message.ct != Some(Heartbeat) => message
//    } >>> ZPipeline.mapChunks { responseMessages =>
//      for {
//        message <- responseMessages
//        marketChange <- message.mc.getOrElse(Nil).toList
//      } yield marketChange
//    }

//  val kafkaPublishMarketChanges: ZPipeline[ManagedKafkaService, Throwable, ResponseMessage, MarketChange] =
//    flatMapMarketChangesPipeline >>> ZPipeline.mapZIO { marketChange =>
//      for {
//        managedKafkaService <- ZIO.service[ManagedKafkaService]
//        _ <- managedKafkaService.publishMarketChange(marketChange)
//      } yield marketChange
//    }

//  extension (zPipeline: ZPipeline[R, E, In, Out])
//    def flatMap[Out1](f: Out => Out1): ZPipeline[R, E, In, Out1] = zPipeline >>> ZPipeline.map[In, Out1]()

  val kafkaPublishMarketChangeMessages: ZPipeline[ManagedKafkaService, Throwable, ResponseMessage, Unit] =
    ZPipeline.collect {
      case msg: MarketChangeMessage if msg.ct != Some(Heartbeat) => msg
    } >>> ZPipeline.mapZIO { marketChangeMessage =>
      ZIO.serviceWithZIO[ManagedKafkaService](_.publishMarketChangeMessage(marketChangeMessage))
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

  val displayHeartbeatCarrotPipeline: ZPipeline[Console, Throwable, ResponseMessage, ResponseMessage] = ZPipeline.mapZIO { message =>
    for {
      _ <- message match {
        case msg@MarketChangeMessage(id, Some(Heartbeat), clk, heartbeatMs, pt, initialClk, mc, conflateMs, segmentType, status) =>
          Console.print("🥕")
        case _ => ZIO.unit
      }
    } yield message
  }

  def updateLastRemoteHeartbeatPipeline(lastHeartbeat: Ref[Instant]): ZPipeline[Clock & Console, Throwable, ResponseMessage, ResponseMessage] =
    ZPipeline.collect {
      case message: MarketChangeMessage if message.ct == Some(Heartbeat) => message
    } >>> ZPipeline.mapZIO { message =>
      for {
        now <- ZIO.serviceWithZIO[Clock](_.instant)
        _ <- lastHeartbeat.update(_ => now) 
      } yield message
    }
