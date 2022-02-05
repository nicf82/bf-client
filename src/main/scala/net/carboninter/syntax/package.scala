package net.carboninter

import java.time.{Instant, OffsetDateTime, ZoneId, ZonedDateTime}
import java.util.Date
import scala.Console

package object syntax:

  inline def rte(m: String) = (a: Any) => new RuntimeException(m + ": " + a.toString)

  def coutl(ansi: String*)(s: Any) = println(ansi.mkString + s.toString + Console.RESET)
  def cout(ansi: String*)(s: Any) = print(ansi.mkString + s.toString + Console.RESET)

  extension (d: Date)
    def toOffsetDateTime: OffsetDateTime = {
      val i = Instant.ofEpochMilli(d.getTime)
      ZonedDateTime.ofInstant(i, ZoneId.systemDefault()).toOffsetDateTime
    }

  extension (l: Long)
    def toOffsetDateTime: OffsetDateTime = {
      val i = Instant.ofEpochMilli(l)
      ZonedDateTime.ofInstant(i, ZoneId.systemDefault()).toOffsetDateTime
    }
