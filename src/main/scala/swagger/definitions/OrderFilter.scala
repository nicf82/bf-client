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
case class OrderFilter(includeOverallPosition: Option[Boolean] = None, accountIds: Option[Vector[Long]] = None, customerStrategyRefs: Option[Vector[String]] = None, partitionMatchedByStrategyRef: Option[Boolean] = None)
object OrderFilter {
  implicit val encodeOrderFilter: _root_.io.circe.Encoder.AsObject[OrderFilter] = {
    val readOnlyKeys = _root_.scala.Predef.Set[_root_.scala.Predef.String]()
    _root_.io.circe.Encoder.AsObject.instance[OrderFilter](a => _root_.io.circe.JsonObject.fromIterable(_root_.scala.Vector(("includeOverallPosition", a.includeOverallPosition.asJson), ("accountIds", a.accountIds.asJson), ("customerStrategyRefs", a.customerStrategyRefs.asJson), ("partitionMatchedByStrategyRef", a.partitionMatchedByStrategyRef.asJson)))).mapJsonObject(_.filterKeys(key => !(readOnlyKeys contains key)))
  }
  implicit val decodeOrderFilter: _root_.io.circe.Decoder[OrderFilter] = new _root_.io.circe.Decoder[OrderFilter] { final def apply(c: _root_.io.circe.HCursor): _root_.io.circe.Decoder.Result[OrderFilter] = for (v0 <- c.downField("includeOverallPosition").as[Option[Boolean]]; v1 <- c.downField("accountIds").as[Option[Vector[Long]]]; v2 <- c.downField("customerStrategyRefs").as[Option[Vector[String]]]; v3 <- c.downField("partitionMatchedByStrategyRef").as[Option[Boolean]]) yield OrderFilter(v0, v1, v2, v3) }
}