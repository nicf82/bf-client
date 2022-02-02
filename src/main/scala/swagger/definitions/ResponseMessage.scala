/*
 * This file was generated by guardrail (https://github.com/guardrail-dev/guardrail).
 * Modifications will be overwritten; instead edit the OpenAPI/Swagger spec file.
 */
package swagger.definitions

import cats.syntax.either._
import io.circe._
import io.circe.syntax._
import cats.implicits._
import _root_.swagger.Implicits._
import cats.syntax.either._
trait ResponseMessage { def id: Option[Int] }
object ResponseMessage {
  val discriminator: String = "op"
  implicit val encoder: _root_.io.circe.Encoder[ResponseMessage] = _root_.io.circe.Encoder.instance({
    case e: ConnectionMessage =>
      e.asJsonObject.add(discriminator, "connection".asJson).asJson
    case e: StatusMessage =>
      e.asJsonObject.add(discriminator, "status".asJson).asJson
    case e: MarketChangeMessage =>
      e.asJsonObject.add(discriminator, "mcm".asJson).asJson
    case e: OrderChangeMessage =>
      e.asJsonObject.add(discriminator, "ocm".asJson).asJson
  })
  implicit val decoder: _root_.io.circe.Decoder[ResponseMessage] = _root_.io.circe.Decoder.instance { c => 
    val discriminatorCursor = c.downField(discriminator)
    discriminatorCursor.as[String].flatMap({
      case "connection" =>
        c.as[ConnectionMessage]
      case "status" =>
        c.as[StatusMessage]
      case "mcm" =>
        c.as[MarketChangeMessage]
      case "ocm" =>
        c.as[OrderChangeMessage]
      case tpe =>
        _root_.scala.Left(DecodingFailure("Unknown value " ++ tpe ++ " (valid: connection, status, mcm, ocm)", discriminatorCursor.history))
    })
  }
}