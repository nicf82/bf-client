package net.carboninter.kafka

import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.betfair.BetfairIdentityService
import org.apache.kafka.clients.producer
import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.serialization.{Deserializer, Serde, Serdes, Serializer, StringDeserializer, StringSerializer}
import org.apache.kafka.clients.consumer.*

import java.util
import org.slf4j.{Logger, LoggerFactory}
import swagger.definitions.{MarketChange, MarketChangeMessage, ResponseMessage}
import zio.stream.*
import zio.{UIO, *}
import zio.Duration.*

import scala.jdk.CollectionConverters.*
import java.util.Properties
import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import net.carboninter.Main
import net.carboninter.models.Command

trait ManagedKafkaService:
  def splitStreams: URIO[Clock, (UStream[Command], UStream[MarketChangeMessage])]
  def publishMarketChangeMessage(marketChangeMessage: MarketChangeMessage): Task[Unit] //Change to a sink


object ManagedKafkaService:

  val live: ZLayer[AppConfigService & LoggerAdapter, Throwable, ManagedKafkaService] = {
    val producer = ZManaged.acquireReleaseWith(createKafkaProducer)(p => ZIO.succeed(p.close())).toLayer
    val consumer = ZManaged.acquireReleaseWith(createConsumer)(p => ZIO.succeed(p.close())).toLayer  //TODO - how to handle errors at this location???
    (producer ++ consumer) >>>
      ZLayer.fromZIO {
        for {
          appConfigService <- ZIO.service[AppConfigService]
          loggerAdapter <- ZIO.service[LoggerAdapter]
          producer <- ZIO.service[KafkaProducer[String, String]]
          consumer <- ZIO.service[KafkaConsumer[String, Json]]
        } yield LiveManagedKafkaService(appConfigService, loggerAdapter, producer, consumer)
      }
  }

  private def createKafkaProducer: Task[KafkaProducer[String, String]] = ZIO.attempt {
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

  private def createConsumer: Task[KafkaConsumer[String, Json]] = ZIO.attempt {
    val bootstrapServers: String = "127.0.0.1:9092"
    val groupId: String = "bf-client"
    // create consumer configs
    val properties = new Properties
    properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[JsonDeserializer].getName)
    properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true") // disable auto commit of offsets
    properties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100")
    // create consumer
    val consumer = new KafkaConsumer[String, Json](properties)
    consumer.subscribe(util.Arrays.asList("commands", "market_change_messages_raw"))
    println("Created consumer")
    consumer
  }


case class LiveManagedKafkaService(appConfigService: AppConfigService, loggerAdapter: LoggerAdapter, producer: KafkaProducer[String, String], consumer: KafkaConsumer[String, Json]) extends ManagedKafkaService:

  implicit val _: Logger = LoggerFactory.getLogger(getClass)



  private def unifiedStream: UStream[ConsumerRecords[String, Json]] =
    ZStream.asyncZIO[Any, Nothing, ConsumerRecords[String, Json]] { emit =>
      (for {
        consumerRecords  <- ZIO.attemptBlocking(consumer.poll(100.millis))
        recordCount      <- ZIO.succeed(consumerRecords.count())
        _                <- ZIO.succeed {
                              if(recordCount > 0) emit( ZIO.succeed(Chunk(consumerRecords)) )
                              else ()
                            }
      } yield ()).forever.fork
    }

  override def splitStreams: URIO[Clock, (UStream[Command], UStream[MarketChangeMessage])] = for {
    commandQueue   <- ZQueue.bounded[Command](256)
    mcmQueue       <- ZQueue.bounded[MarketChangeMessage](256)
    ts             <- unifiedStream.tap { cr =>
                        for {
                          
                          commands <- ZIO.foreach(cr.records("commands").asScala.toList) { command =>
                                        ZIO.fromEither(command.value().as[Command]).map(Some(_)).catchAll { e =>
                                          for {
                                            _ <- loggerAdapter.warn("Error consuming command from Kafka", e)
                                          } yield None
                                        }
                                      }
                          _        <- commandQueue.offerAll(commands.flatten)
                          
                          mcmraw   <- ZIO.foreach(cr.records("market_change_messages_raw").asScala.toList) { mcm =>
                                        ZIO.fromEither(mcm.value().as[MarketChangeMessage]).map(Some(_)).catchAll { e =>
                                          for {
                                            _ <- loggerAdapter.warn("Error consuming MarketChangeMessage from Kafka", e)
                                          } yield None
                                        }
                                      }
                          _        <- mcmQueue.offerAll(mcmraw.flatten)
                        } yield ()
                      }.runDrain.fork
    } yield (
      ZStream.fromQueue(commandQueue),
      ZStream.fromQueue(mcmQueue)
    )

  override def publishMarketChangeMessage(marketChangeMessage: MarketChangeMessage): Task[Unit] = Task.asyncZIO[Unit] { cb =>

    for {
      _ <- ZIO.attempt {
        producer.send(new ProducerRecord[String, String]("market_change_messages_raw", marketChangeMessage.pt.toString, marketChangeMessage.asJson.noSpaces), new Callback() {
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


