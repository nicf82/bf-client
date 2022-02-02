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
case class AllResponseTypesExample(opTypes: Option[AllResponseTypesExample.OpTypes] = None, marketChangeMessage: Option[MarketChangeMessage] = None, connection: Option[ConnectionMessage] = None, orderChangeMessage: Option[OrderChangeMessage] = None, status: Option[StatusMessage] = None)
object AllResponseTypesExample {
  implicit val encodeAllResponseTypesExample: _root_.io.circe.Encoder.AsObject[AllResponseTypesExample] = {
    val readOnlyKeys = _root_.scala.Predef.Set[_root_.scala.Predef.String]()
    _root_.io.circe.Encoder.AsObject.instance[AllResponseTypesExample](a => _root_.io.circe.JsonObject.fromIterable(_root_.scala.Vector(("opTypes", a.opTypes.asJson), ("marketChangeMessage", a.marketChangeMessage.asJson), ("connection", a.connection.asJson), ("orderChangeMessage", a.orderChangeMessage.asJson), ("status", a.status.asJson)))).mapJsonObject(_.filterKeys(key => !(readOnlyKeys contains key)))
  }
  implicit val decodeAllResponseTypesExample: _root_.io.circe.Decoder[AllResponseTypesExample] = new _root_.io.circe.Decoder[AllResponseTypesExample] { final def apply(c: _root_.io.circe.HCursor): _root_.io.circe.Decoder.Result[AllResponseTypesExample] = for (v0 <- c.downField("opTypes").as[Option[AllResponseTypesExample.OpTypes]]; v1 <- c.downField("marketChangeMessage").as[Option[MarketChangeMessage]]; v2 <- c.downField("connection").as[Option[ConnectionMessage]]; v3 <- c.downField("orderChangeMessage").as[Option[OrderChangeMessage]]; v4 <- c.downField("status").as[Option[StatusMessage]]) yield AllResponseTypesExample(v0, v1, v2, v3, v4) }
  sealed abstract class OpTypes(val value: String) extends _root_.scala.Product with _root_.scala.Serializable { override def toString: String = value.toString }
  object OpTypes {
    object members {
      case object Connection extends OpTypes("connection")
      case object Status extends OpTypes("status")
      case object Mcm extends OpTypes("mcm")
      case object Ocm extends OpTypes("ocm")
    }
    val Connection: OpTypes = members.Connection
    val Status: OpTypes = members.Status
    val Mcm: OpTypes = members.Mcm
    val Ocm: OpTypes = members.Ocm
    val values = _root_.scala.Vector(Connection, Status, Mcm, Ocm)
    implicit val encodeOpTypes: _root_.io.circe.Encoder[OpTypes] = _root_.io.circe.Encoder[String].contramap(_.value)
    implicit val decodeOpTypes: _root_.io.circe.Decoder[OpTypes] = _root_.io.circe.Decoder[String].emap(value => from(value).toRight(s"$value not a member of OpTypes"))
    implicit val showOpTypes: Show[OpTypes] = Show[String].contramap[OpTypes](_.value)
    def from(value: String): _root_.scala.Option[OpTypes] = values.find(_.value == value)
    implicit val order: cats.Order[OpTypes] = cats.Order.by[OpTypes, Int](values.indexOf)
  }
}