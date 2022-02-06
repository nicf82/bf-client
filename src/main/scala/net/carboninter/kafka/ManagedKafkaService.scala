package net.carboninter.kafka

import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.services.BetfairIdentityService
import org.apache.kafka.clients.producer
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.{Logger, LoggerFactory}
import swagger.definitions.MarketChange
import zio.stream.*
import zio.*

import java.util.Properties

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*

trait ManagedKafkaService:
  def publishMarketChange(marketChange: MarketChange): ZIO[KafkaProducer[String, String], Throwable, Unit]

object ManagedKafkaService:

  val producer: TaskLayer[KafkaProducer[String, String]] = ZManaged.acquireReleaseAttemptWith(createKafkaProducer)(p => p.close()).toLayer

  val live: URLayer[AppConfigService & LoggerAdapter, ManagedKafkaService] =
    (LiveManagedKafkaService(_, _)).toLayer[ManagedKafkaService]

  private def createKafkaProducer: KafkaProducer[String, String] = {
    val bootstrapServers = "127.0.0.1:9092"
    // create Producer properties
    val properties = new Properties
    properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    // create safe Producer
    properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
    properties.setProperty(ProducerConfig.ACKS_CONFIG, "all")
    properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE))
    properties.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5") // kafka 2.0 >= 1.1 so we can keep this as 5. Use 1 otherwise.

    // high throughput producer (at the expense of a bit of latency and CPU usage)
    properties.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy")
    properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "20")
    properties.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32 * 1024)) // 32 KB batch size

    // create the producer
    new KafkaProducer[String, String](properties)

  }

case class LiveManagedKafkaService(appConfigService: AppConfigService, loggerAdapter: LoggerAdapter) extends ManagedKafkaService:

  implicit val _: Logger = LoggerFactory.getLogger(getClass)

  override def publishMarketChange(marketChange: MarketChange): ZIO[KafkaProducer[String, String], Throwable, Unit] = ZIO.asyncZIO[KafkaProducer[String, String], Throwable, Unit] { cb =>

    for {
      producer <- ZIO.service[KafkaProducer[String, String]]
      _ <- ZIO.attempt {
        producer.send(new ProducerRecord[String, String]("market_changes_raw", marketChange.id, marketChange.asJson.noSpaces), new Callback() {
          override def onCompletion(recordMetadata: RecordMetadata, e: Exception): Unit = {
            if (e == null) {
              cb(ZIO.unit)
            } else {
              loggerAdapter.warn("Kafka send failed", e)
              cb(ZIO.fail(e))
            }
          }
        })
      }
    } yield ()
  }


