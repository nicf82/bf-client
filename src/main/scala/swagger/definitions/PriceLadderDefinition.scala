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
case class PriceLadderDefinition(`type`: Option[PriceLadderDefinition.Type] = None)
object PriceLadderDefinition {
  implicit val encodePriceLadderDefinition: _root_.io.circe.Encoder.AsObject[PriceLadderDefinition] = {
    val readOnlyKeys = _root_.scala.Predef.Set[_root_.scala.Predef.String]()
    _root_.io.circe.Encoder.AsObject.instance[PriceLadderDefinition](a => _root_.io.circe.JsonObject.fromIterable(_root_.scala.Vector(("type", a.`type`.asJson)))).mapJsonObject(_.filterKeys(key => !(readOnlyKeys contains key)))
  }
  implicit val decodePriceLadderDefinition: _root_.io.circe.Decoder[PriceLadderDefinition] = new _root_.io.circe.Decoder[PriceLadderDefinition] { final def apply(c: _root_.io.circe.HCursor): _root_.io.circe.Decoder.Result[PriceLadderDefinition] = for (v0 <- c.downField("type").as[Option[PriceLadderDefinition.Type]]) yield PriceLadderDefinition(v0) }
  sealed abstract class Type(val value: String) extends _root_.scala.Product with _root_.scala.Serializable { override def toString: String = value.toString }
  object Type {
    object members {
      case object Classic extends Type("CLASSIC")
      case object Finest extends Type("FINEST")
      case object LineRange extends Type("LINE_RANGE")
    }
    val Classic: Type = members.Classic
    val Finest: Type = members.Finest
    val LineRange: Type = members.LineRange
    val values = _root_.scala.Vector(Classic, Finest, LineRange)
    implicit val encodeType: _root_.io.circe.Encoder[Type] = _root_.io.circe.Encoder[String].contramap(_.value)
    implicit val decodeType: _root_.io.circe.Decoder[Type] = _root_.io.circe.Decoder[String].emap(value => from(value).toRight(s"$value not a member of Type"))
    implicit val showType: Show[Type] = Show[String].contramap[Type](_.value)
    def from(value: String): _root_.scala.Option[Type] = values.find(_.value == value)
    implicit val order: cats.Order[Type] = cats.Order.by[Type, Int](values.indexOf)
  }
}