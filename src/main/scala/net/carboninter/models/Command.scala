package net.carboninter.models

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.decode
import io.circe.generic.auto.*
import org.apache.kafka.common.serialization.*
import org.slf4j.LoggerFactory
import cats.implicits.*

trait Command

object Command {

  val logger = LoggerFactory.getLogger(getClass)

  val discriminator: String = "cmd"

  implicit val encoder: Encoder[Command] = Encoder.instance({
    case e: SubscribeCommand =>
      e.asJsonObject.add(discriminator, "connection".asJson).asJson
  })

  implicit val decoder: Decoder[Command] = Decoder.instance { c =>
    val discriminatorCursor = c.downField(discriminator)
    discriminatorCursor.as[String].flatMap({
      case "subscribe" =>
        c.as[SubscribeCommand]
      case tpe =>
        Left(DecodingFailure("Unknown value " ++ tpe ++ " (valid: cmd)", discriminatorCursor.history))
    })
  }
}

case class SubscribeCommand(marketIds: List[String]) extends Command





