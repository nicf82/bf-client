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
case class OrderChangeMessage(id: Option[Int] = None, ct: Option[OrderChangeMessage.Ct] = None, clk: Option[String] = None, heartbeatMs: Option[Long] = None, pt: Option[Long] = None, oc: Option[_root_.scala.Vector[OrderMarketChange]] = None, initialClk: Option[String] = None, conflateMs: Option[Long] = None, segmentType: Option[OrderChangeMessage.SegmentType] = None, status: Option[Int] = None) extends ResponseMessage
object OrderChangeMessage {
  implicit val encodeOrderChangeMessage: _root_.io.circe.Encoder.AsObject[OrderChangeMessage] = {
    val readOnlyKeys = _root_.scala.Predef.Set[_root_.scala.Predef.String]()
    _root_.io.circe.Encoder.AsObject.instance[OrderChangeMessage](a => _root_.io.circe.JsonObject.fromIterable(_root_.scala.Vector(("id", a.id.asJson), ("ct", a.ct.asJson), ("clk", a.clk.asJson), ("heartbeatMs", a.heartbeatMs.asJson), ("pt", a.pt.asJson), ("oc", a.oc.asJson), ("initialClk", a.initialClk.asJson), ("conflateMs", a.conflateMs.asJson), ("segmentType", a.segmentType.asJson), ("status", a.status.asJson)))).mapJsonObject(_.filterKeys(key => !(readOnlyKeys contains key)))
  }
  implicit val decodeOrderChangeMessage: _root_.io.circe.Decoder[OrderChangeMessage] = new _root_.io.circe.Decoder[OrderChangeMessage] { final def apply(c: _root_.io.circe.HCursor): _root_.io.circe.Decoder.Result[OrderChangeMessage] = for (v0 <- c.downField("id").as[Option[Int]]; v1 <- c.downField("ct").as[Option[OrderChangeMessage.Ct]]; v2 <- c.downField("clk").as[Option[String]]; v3 <- c.downField("heartbeatMs").as[Option[Long]]; v4 <- c.downField("pt").as[Option[Long]]; v5 <- c.downField("oc").as[Option[_root_.scala.Vector[OrderMarketChange]]]; v6 <- c.downField("initialClk").as[Option[String]]; v7 <- c.downField("conflateMs").as[Option[Long]]; v8 <- c.downField("segmentType").as[Option[OrderChangeMessage.SegmentType]]; v9 <- c.downField("status").as[Option[Int]]) yield OrderChangeMessage(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9) }
  sealed abstract class Ct(val value: String) extends _root_.scala.Product with _root_.scala.Serializable { override def toString: String = value.toString }
  object Ct {
    object members {
      case object SubImage extends Ct("SUB_IMAGE")
      case object ResubDelta extends Ct("RESUB_DELTA")
      case object Heartbeat extends Ct("HEARTBEAT")
    }
    val SubImage: Ct = members.SubImage
    val ResubDelta: Ct = members.ResubDelta
    val Heartbeat: Ct = members.Heartbeat
    val values = _root_.scala.Vector(SubImage, ResubDelta, Heartbeat)
    implicit val encodeCt: _root_.io.circe.Encoder[Ct] = _root_.io.circe.Encoder[String].contramap(_.value)
    implicit val decodeCt: _root_.io.circe.Decoder[Ct] = _root_.io.circe.Decoder[String].emap(value => from(value).toRight(s"$value not a member of Ct"))
    implicit val showCt: Show[Ct] = Show[String].contramap[Ct](_.value)
    def from(value: String): _root_.scala.Option[Ct] = values.find(_.value == value)
    implicit val order: cats.Order[Ct] = cats.Order.by[Ct, Int](values.indexOf)
  }
  sealed abstract class SegmentType(val value: String) extends _root_.scala.Product with _root_.scala.Serializable { override def toString: String = value.toString }
  object SegmentType {
    object members {
      case object SegStart extends SegmentType("SEG_START")
      case object Seg extends SegmentType("SEG")
      case object SegEnd extends SegmentType("SEG_END")
    }
    val SegStart: SegmentType = members.SegStart
    val Seg: SegmentType = members.Seg
    val SegEnd: SegmentType = members.SegEnd
    val values = _root_.scala.Vector(SegStart, Seg, SegEnd)
    implicit val encodeSegmentType: _root_.io.circe.Encoder[SegmentType] = _root_.io.circe.Encoder[String].contramap(_.value)
    implicit val decodeSegmentType: _root_.io.circe.Decoder[SegmentType] = _root_.io.circe.Decoder[String].emap(value => from(value).toRight(s"$value not a member of SegmentType"))
    implicit val showSegmentType: Show[SegmentType] = Show[String].contramap[SegmentType](_.value)
    def from(value: String): _root_.scala.Option[SegmentType] = values.find(_.value == value)
    implicit val order: cats.Order[SegmentType] = cats.Order.by[SegmentType, Int](values.indexOf)
  }
}