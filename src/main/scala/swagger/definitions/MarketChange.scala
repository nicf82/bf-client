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
case class MarketChange(rc: Option[_root_.scala.Vector[RunnerChange]] = None, img: Option[Boolean] = None, tv: Option[Double] = None, con: Option[Boolean] = None, marketDefinition: Option[MarketDefinition] = None, id: String)
object MarketChange {
  implicit val encodeMarketChange: _root_.io.circe.Encoder.AsObject[MarketChange] = {
    val readOnlyKeys = _root_.scala.Predef.Set[_root_.scala.Predef.String]()
    _root_.io.circe.Encoder.AsObject.instance[MarketChange](a => _root_.io.circe.JsonObject.fromIterable(_root_.scala.Vector(("rc", a.rc.asJson), ("img", a.img.asJson), ("tv", a.tv.asJson), ("con", a.con.asJson), ("marketDefinition", a.marketDefinition.asJson), ("id", a.id.asJson)))).mapJsonObject(_.filterKeys(key => !(readOnlyKeys contains key)))
  }
  implicit val decodeMarketChange: _root_.io.circe.Decoder[MarketChange] = new _root_.io.circe.Decoder[MarketChange] { final def apply(c: _root_.io.circe.HCursor): _root_.io.circe.Decoder.Result[MarketChange] = for (v0 <- c.downField("rc").as[Option[_root_.scala.Vector[RunnerChange]]]; v1 <- c.downField("img").as[Option[Boolean]]; v2 <- c.downField("tv").as[Option[Double]]; v3 <- c.downField("con").as[Option[Boolean]]; v4 <- c.downField("marketDefinition").as[Option[MarketDefinition]]; v5 <- c.downField("id").as[String]) yield MarketChange(v0, v1, v2, v3, v4, v5) }
}