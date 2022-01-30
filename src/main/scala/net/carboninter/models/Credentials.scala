package net.carboninter.models

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.time.Instant
import scala.util.Random

case class Credentials(payload: Credentials.Payload, expires: Instant)

object Credentials {

  case class Payload(token: String, product: String, status: String, error: String)

  implicit val decoderP: JsonDecoder[Credentials.Payload] = DeriveJsonDecoder.gen[Credentials.Payload]
  implicit val encoderP: JsonEncoder[Credentials.Payload] = DeriveJsonEncoder.gen[Credentials.Payload]
  implicit val decoder: JsonDecoder[Credentials] = DeriveJsonDecoder.gen[Credentials]
  implicit val encoder: JsonEncoder[Credentials] = DeriveJsonEncoder.gen[Credentials]

  val empty = Credentials(Payload("", "", "", ""), Instant.EPOCH)

//  def newToken: String = Random.alphanumeric.filter(_.isLetter).take(5).mkString
//  def expiringInSeconds(s: Int) = Credentials(newToken, Instant.now.plusSeconds(s))
}