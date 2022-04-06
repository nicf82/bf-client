package net.carboninter.kafka

import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
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
import net.carboninter.models.{Command, MarketChangeEnvelope}
import org.apache.kafka.clients.admin.{Admin, AdminClientConfig, CreateTopicsOptions, NewTopic}
import org.apache.kafka.common.config.TopicConfig

trait ManagedKafkaService:
  def splitStreams: ZIO[Clock & AppConfigService, Throwable, (UStream[Command], UStream[MarketChangeMessage])]
  def unifiedStream: ZStream[Any, Throwable, ConsumerRecords[String, Json]]
  def marketChangeMessageDeltaTopicSink: ZSink[AppConfigService, Throwable, MarketChangeMessage, Nothing, Unit]
  def marketChangeTopicSink: ZSink[AppConfigService, Throwable, MarketChangeEnvelope, Nothing, Unit]


object ManagedKafkaService:

  val live: ZLayer[AppConfigService & LoggerAdapter, Throwable, ManagedKafkaService] = {
    val producer = ZManaged.acquireReleaseWith(createKafkaProducer)(p => ZIO.succeed(p.close())).toLayer
    val consumer = createKafkaConsumer.toLayer
    val admin    = createKafkaAdmin.toLayer
    (admin >>> (producer ++ consumer)) >>>
      ZLayer.fromZIO {
        for {
          appConfigService <- ZIO.service[AppConfigService]
          loggerAdapter    <- ZIO.service[LoggerAdapter]
          producer         <- ZIO.service[KafkaProducer[String, String]]
          consumer         <- ZIO.service[KafkaConsumer[String, Json]]
        } yield LiveManagedKafkaService(appConfigService, loggerAdapter, producer, consumer)
      }
  }

  private def createKafkaAdmin: RIO[AppConfigService, Admin] = for {
    config <- ZIO.serviceWithZIO[AppConfigService](_.getAppConfig.map(_.kafka))
    admin  <- ZIO.attempt {
      val properties = new Properties
      properties.setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
      Admin.create(properties)
    }
    _      <- ZIO.attemptBlocking {


      val topics = admin.listTopics().names()
      val existingTopics = topics.get().asScala.toList

      val desiredTopics = config.topics.all
        .filter(t => !existingTopics.contains(t.name))

      if(!desiredTopics.isEmpty) {
        val compactTopicConfig = Map(TopicConfig.CLEANUP_POLICY_CONFIG -> TopicConfig.CLEANUP_POLICY_COMPACT)

        val newTopics = desiredTopics map { conf =>
          new NewTopic(conf.name, conf.partitions, conf.replication).configs(conf.config.getOrElse(Map.empty).asJava)
        }

        val res = admin.createTopics(newTopics.asJava)
        res.all().get()
      }
    }
  } yield admin

  private def createKafkaProducer: RIO[AppConfigService, KafkaProducer[String, String]] = for {
    config <- ZIO.serviceWithZIO[AppConfigService](_.getAppConfig.map(_.kafka))
    producer <- ZIO.attempt {
      // create Producer properties
      val properties = new Properties
      properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
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
  } yield producer

  private def createKafkaConsumer: RIO[Admin & AppConfigService, KafkaConsumer[String, Json]] = for {
    config <- ZIO.serviceWithZIO[AppConfigService](_.getAppConfig.map(_.kafka))
    consumer <- ZIO.attempt {
      // create consumer configs
      val properties = new Properties
      properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
      properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
      properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[JsonDeserializer].getName)
      properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, config.groupId)
      properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true") // disable auto commit of offsets
      properties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100")
      // create consumer
      val consumer = new KafkaConsumer[String, Json](properties)
      val subscribeTopics = config.topics.all
        .filter(_.subscribe).map(_.name)
      consumer.subscribe(subscribeTopics.asJava)
      consumer
    }
  } yield consumer

case class LiveManagedKafkaService(
  appConfigService: AppConfigService,
  loggerAdapter: LoggerAdapter,
  producer: KafkaProducer[String, String],
  consumer: KafkaConsumer[String, Json]
) extends ManagedKafkaService:

  import ManagedKafkaService._
  implicit val _: Logger = LoggerFactory.getLogger(getClass)
  
  override def unifiedStream: ZStream[Any, Throwable, ConsumerRecords[String, Json]] =
    ZStream.repeat(())
      .mapZIO { _ =>
        ZIO.attemptBlocking(consumer.poll(1000.millis))
      }
      .filter(_.count() > 0)

  override def splitStreams: ZIO[Clock & AppConfigService, Throwable, (UStream[Command], UStream[MarketChangeMessage])] = for {
    commandQueue   <- ZQueue.bounded[Command](256)
    mcmQueue       <- ZQueue.bounded[MarketChangeMessage](256)
    topics <- ZIO.serviceWithZIO[AppConfigService](_.getAppConfig.map(_.kafka.topics))
    ts             <- unifiedStream.tap { cr =>
      for {

        commands <- ZIO.foreach(cr.records(topics.commandsTopic.name).asScala.toList) { command =>
                      ZIO.fromEither(command.value().as[Command]).map(Some(_)).catchAll { e =>
                        for {
                          _ <- loggerAdapter.warn("Error consuming command from Kafka", e)
                        } yield None
                      }
                    }
        _        <- commandQueue.offerAll(commands.flatten)

        deltas   <- ZIO.foreach(cr.records(topics.marketChangeMessageDeltasTopic.name).asScala.toList) { mcm =>
                      ZIO.fromEither(mcm.value().as[MarketChangeMessage]).map(Some(_)).catchAll { e =>
                        for {
                          _ <- loggerAdapter.warn("Error consuming MarketChangeMessage from Kafka", e)
                        } yield None
                      }
                    }
        _        <- mcmQueue.offerAll(deltas.flatten)
      } yield ()
    }.runDrain.fork
    } yield (
      ZStream.fromQueue(commandQueue),
      ZStream.fromQueue(mcmQueue)
    )

  override val marketChangeMessageDeltaTopicSink: ZSink[AppConfigService, Throwable, MarketChangeMessage, Nothing, Unit] = ZSink.foreach[AppConfigService, Throwable, MarketChangeMessage] { marketChangeMessage =>
    ZIO.asyncZIO[AppConfigService, Throwable, Unit] { cb =>
      for {
        topics <- ZIO.serviceWithZIO[AppConfigService](_.getAppConfig.map(_.kafka.topics))
        _ <- ZIO.attempt {
          producer.send(new ProducerRecord[String, String](topics.marketChangeMessageDeltasTopic.name, marketChangeMessage.pt.toString, marketChangeMessage.asJson.noSpaces), new Callback() {
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
  }


  override val marketChangeTopicSink: ZSink[AppConfigService, Throwable, MarketChangeEnvelope, Nothing, Unit] = ZSink.foreach[AppConfigService, Throwable, MarketChangeEnvelope] { marketChangeEnvelope =>
    ZIO.asyncZIO[AppConfigService, Throwable, Unit] { cb =>
      for {
        topics <- ZIO.serviceWithZIO[AppConfigService](_.getAppConfig.map(_.kafka.topics))
        _ <- ZIO.attempt {
          producer.send(new ProducerRecord[String, String](topics.marketChangesTopic.name, marketChangeEnvelope.marketChange.id, marketChangeEnvelope.asJson.noSpaces), new Callback() {
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
  }


