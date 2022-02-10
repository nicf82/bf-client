package net.carboninter.models

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import swagger.definitions.MarketChange

case class MarketChangeEnvelope(marketChange: MarketChange, isDelta: Boolean) {
  def withMarketChange(marketChange: MarketChange) = this.copy(marketChange = marketChange)
}
object MarketChangeEnvelope {
  implicit val codec: Codec[MarketChangeEnvelope] = deriveCodec[MarketChangeEnvelope]
}