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
case class KeyLineSelection(id: Option[Long] = None, hc: Option[Double] = None)
object KeyLineSelection {
  implicit val encodeKeyLineSelection: _root_.io.circe.Encoder.AsObject[KeyLineSelection] = {
    val readOnlyKeys = _root_.scala.Predef.Set[_root_.scala.Predef.String]()
    _root_.io.circe.Encoder.AsObject.instance[KeyLineSelection](a => _root_.io.circe.JsonObject.fromIterable(_root_.scala.Vector(("id", a.id.asJson), ("hc", a.hc.asJson)))).mapJsonObject(_.filterKeys(key => !(readOnlyKeys contains key)))
  }
  implicit val decodeKeyLineSelection: _root_.io.circe.Decoder[KeyLineSelection] = new _root_.io.circe.Decoder[KeyLineSelection] { final def apply(c: _root_.io.circe.HCursor): _root_.io.circe.Decoder.Result[KeyLineSelection] = for (v0 <- c.downField("id").as[Option[Long]]; v1 <- c.downField("hc").as[Option[Double]]) yield KeyLineSelection(v0, v1) }
}