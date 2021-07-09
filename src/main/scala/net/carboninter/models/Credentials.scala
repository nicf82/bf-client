package net.carboninter.models

import java.time.Instant
import scala.util.Random

case class Credentials(token: String, expires: Instant)

object Credentials {
  def newToken: String = Random.alphanumeric.filter(_.isLetter).take(5).mkString
  def expiringInSeconds(s: Int) = Credentials(newToken, Instant.now.plusSeconds(s))
}