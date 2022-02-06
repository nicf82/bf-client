package net.carboninter.services

import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.serialization.StringSerializer
import swagger.definitions.*

import java.util.Properties
import zio.*
import io.circe.syntax.*
import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.syntax.*
import org.slf4j.{Logger, LoggerFactory}
import scala.{Console => C}

trait MarketChangePublisher:
  def handleMarketChangeMessage(mcm: MarketChangeMessage): RIO[Clock & BetfairService, Unit]
  def publish(marketChange: MarketChange): IO[Throwable, RecordMetadata]


object MarketChangePublisher:
  def live: ZLayer[KafkaProducer[String, String] & AppConfigService & LoggerAdapter, Throwable, MarketChangePublisher] = ZLayer.fromZIO {
    for {
      loggerAdapter <- ZIO.service[LoggerAdapter]
      configService <- ZIO.service[AppConfigService]
      producer      <- ZIO.service[KafkaProducer[String, String]]
    } yield LiveMarketChangePublisher(configService, loggerAdapter, producer)
  }

class LiveMarketChangePublisher(appConfigService: AppConfigService, loggerAdapter: LoggerAdapter, producer: KafkaProducer[String, String]) extends MarketChangePublisher:

  implicit val _: Logger = LoggerFactory.getLogger(getClass)

  //TODO - 1st draft. Dont publish the raw message like this - it needs to embelish whats in the cache to get the full picture
  override def publish(marketChange: MarketChange) = IO.asyncZIO[Throwable, RecordMetadata] { cb =>
    //TODO - this will not be sent as a string! Avro?
    for {
      _ <- ZIO.attempt {
        val payload = marketChange.asJson.noSpaces
        producer.send(new ProducerRecord[String, String]("market_changes_raw", marketChange.id, payload), new Callback() {
          override def onCompletion(recordMetadata: RecordMetadata, e: Exception): Unit = {
            if (e == null) {
              cb(IO.succeed(recordMetadata))
            } else {
              loggerAdapter.warn("Kafka send failed", e)
              cb(IO.fail(e))
            }
          }
        })
      }
    } yield ()
  }

  def handleMarketChangeMessage(mcm: MarketChangeMessage): RIO[Clock & BetfairService, Unit] = for {
    publishTime    <- ZIO.fromOption(mcm.pt).mapError(rte("No publishTime"))
    changeType     =  mcm.ct
    _              =  coutl(C.BOLD, C.MAGENTA)(s"\npt: ${publishTime.toOffsetDateTime}, ct: $changeType")
    _              =  coutl(C.BOLD, C.MAGENTA)("======================================")
    _              <- ZIO.foreach(mcm.mc.getOrElse(Nil).toList)(handleMarketChange(_))
  } yield ()

  def handleMarketChange(mc: MarketChange): RIO[Clock & BetfairService, Unit] = for {
    _              <- publish(mc)
    marketId       =  mc.id
    marketDef      =  mc.marketDefinition
    venue          =  marketDef.flatMap(_.venue)
    marketTime     =  marketDef.flatMap(_.marketTime)
    bettingType    =  marketDef.flatMap(_.bettingType)
    marketType     =  marketDef.flatMap(_.marketType)
    _              =  coutl(C.BOLD, C.GREEN)(s"$marketId $venue $marketTime $bettingType $marketType")
    _              =  coutl(C.BOLD, C.GREEN)("--------------------------------------")
    _              <- ZIO.foreach(mc.rc.getOrElse(Nil).toList)(handleRunnerChange(marketId, _))
  } yield ()

  def handleRunnerChange(marketId: String, runnerChange: RunnerChange): RIO[Clock & BetfairService, Unit] = for {
    betfairService <- ZIO.service[BetfairService]
    rcId            = runnerChange.id
    runner         <- betfairService.getRunner(marketId, rcId).flatMap(ZIO.fromOption).mapError(rte("Couldn't get runner"))
    _               = coutl(C.BOLD, C.YELLOW)(s"🐴 ${rcId} " + runner.name)

    _               = cout(C.RESET)("atb / atl ")
    _              <- ZIO.foreach(runnerChange.atb.map(_.sortBy(_.price)))(handlePriceSize(C.BLUE))
    _              <- ZIO.foreach(runnerChange.atl.map(_.sortBy(_.price)))(handlePriceSize(C.RED))

    _               = cout(C.RESET)("\nspb / spl ")
    _              <- ZIO.foreach(runnerChange.spb.map(_.sortBy(_.price)))(handlePriceSize(C.BLUE))
    _              <- ZIO.foreach(runnerChange.spl.map(_.sortBy(_.price)))(handlePriceSize(C.RED))

    _               = cout(C.RESET)("\ntrd       ")
    _              <- ZIO.foreach(runnerChange.trd.map(_.sortBy(_.price)))(handlePriceSize(C.BOLD))

    _               = cout(C.RESET)("\nbatb / batl")
    _              <- ZIO.foreach(runnerChange.batb.map(_.sortBy(_.level).reverse))(handleLevelPriceSize(C.BLUE))
    _              <- ZIO.foreach(runnerChange.batl.map(_.sortBy(_.level)))(handleLevelPriceSize(C.RED))

    _               = cout(C.RESET)("\nbdatb/bdatl")
    _              <- ZIO.foreach(runnerChange.bdatb.map(_.sortBy(_.level).reverse))(handleLevelPriceSize(C.BLUE))
    _              <- ZIO.foreach(runnerChange.bdatl.map(_.sortBy(_.level)))(handleLevelPriceSize(C.RED))

    _               = print("\n\n")
  } yield ()

  def handlePriceSize(color: String)(values: Vector[PriceSize]): RIO[Clock & BetfairService, Unit] = ZIO.succeed {
    if(values.isEmpty) cout(color, C.UNDERLINED)("Empty list (removed)")
    else values.filter(_.size >= 2).map(a => cout(color)(" "+a))
  }

  def handleLevelPriceSize(color: String)(values: Vector[LevelPriceSize]): RIO[Clock & BetfairService, Unit] = ZIO.succeed {
    if(values.isEmpty) cout(color, C.UNDERLINED)("Empty list (removed)")
    else values.filter(_.size >= 2).map(a => cout(color)(" "+a))
  }

object ManagedKafka:

  def producer: TaskLayer[KafkaProducer[String, String]] = ZManaged.acquireReleaseAttemptWith(createKafkaProducer)(p => p.close()).toLayer

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