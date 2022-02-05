package swagger.definitions

import io.circe.*

case class PriceSize(price: Double, size: Double) {
  override def toString: String = price + "£" + size
}

object PriceSize:
  implicit val decodePriceSize: Decoder[PriceSize] = Decoder.decodeList[Double].emap {
    case price :: size :: Nil => Right(PriceSize(price, size))
    case _ => Left("PriceSize decoder expects a 2-elem array")
  }

  implicit val encodePriceSize: Encoder[PriceSize] = Encoder.encodeList[Double].contramapArray { x =>
    List(x.size, x.price)
  }

case class LevelPriceSize(level: Int, price: Double, size: Double) {
  override def toString: String = price + "£" + size
}

object LevelPriceSize:
  implicit val decodeLevelPriceSize: Decoder[LevelPriceSize] = Decoder.decodeList[Double].emap {
    case level :: price :: size :: Nil => Right(LevelPriceSize(level.toInt, price, size))
    case _ => Left("LevelPriceSize decoder expects a 3-elem array")
  }

  implicit val encodeLevelPriceSize: Encoder[LevelPriceSize] = Encoder.encodeList[Double].contramapArray { x =>
    List(x.size, x.price)
  }