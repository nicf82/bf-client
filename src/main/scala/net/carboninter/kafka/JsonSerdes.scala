package net.carboninter.kafka

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.parse
import net.carboninter.models.Command.getClass
import org.apache.kafka.common.serialization.{Deserializer, Serde, Serdes, Serializer}
import org.slf4j.LoggerFactory
import cats.implicits.*

class JsonSerializer extends Serializer[Json] {
  override def serialize(topic: String, data: Json): Array[Byte] =
    data.asJson.noSpaces.getBytes
}

class JsonDeserializer extends Deserializer[Json] {

  val logger = LoggerFactory.getLogger(getClass)

  override def deserialize(topic: String, data: Array[Byte]): Json = {
    parse(new String(data)) match {
      case Left(error) =>
        logger.warn("Unable to parse as json " + error.show, new RuntimeException(error))
        Json.Null  //Better way?
      case Right(json) => json
    }
  }
}
