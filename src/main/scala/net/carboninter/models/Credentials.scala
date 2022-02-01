package net.carboninter.models


import java.time.Instant
import scala.util.Random

case class Credentials(payload: Credentials.Payload, expires: Instant)

object Credentials {

  case class Payload(token: String, product: String, status: String, error: String)

  val empty = Credentials(Payload("", "", "", ""), Instant.EPOCH)

//  def newToken: String = Random.alphanumeric.filter(_.isLetter).take(5).mkString
//  def expiringInSeconds(s: Int) = Credentials(newToken, Instant.now.plusSeconds(s))
}