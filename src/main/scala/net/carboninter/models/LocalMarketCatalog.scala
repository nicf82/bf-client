package net.carboninter.models

import java.time.{Instant, OffsetDateTime, OffsetTime}

case class LocalRunner(id: Long, name: String, updated: Instant)
case class LocalMarketCatalog(eventVenue: String, eventOpen: OffsetDateTime, marketName: String, marketTime: OffsetDateTime, runners: List[LocalRunner], updated: Instant) {
  def getRunner(id: Long) = runners.filter(_.id==id).headOption
}
