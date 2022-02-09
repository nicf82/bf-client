package net.carboninter

import swagger.definitions.MarketChange
import zio.*

package object betfair:
  type BetfairStreamCounterRef = Ref[Int]
  type MarketChangeCache = Ref[Map[String, MarketChange]]

