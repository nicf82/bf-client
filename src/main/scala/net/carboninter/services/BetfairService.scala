package net.carboninter.services

import com.jbetfairng.*
import com.jbetfairng.entities.*
import com.jbetfairng.enums.MarketProjection.*
import com.jbetfairng.enums.*
import net.carboninter.syntax.*
import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.{Credentials, LocalMarketCatalog, LocalRunner}
import org.slf4j.{Logger, LoggerFactory}
import zio.*

import java.time.{OffsetDateTime, OffsetTime}
import java.util
import scala.jdk.CollectionConverters.*

trait BetfairService:
  def getRunner(marketId: String, runnerId: Long): RIO[Clock, Option[LocalRunner]]
  def getMarketCatalog(marketId: String): RIO[Clock, Option[LocalMarketCatalog]]

object BetfairService:

  val live: URLayer[AppConfigService & LoggerAdapter & BetfairIdentityService, BetfairService] = ZLayer.fromZIO {
    for {
      loggerAdapter          <- ZIO.service[LoggerAdapter]
      configService          <- ZIO.service[AppConfigService]
      config                 <- configService.getAppConfig
      appKey                  = config.betfair.appKey
      client                 <- ZRef.make(new BetfairClient(Exchange.UK, appKey))
      cache                  <- ZRef.make(Map.empty[String, LocalMarketCatalog])
      betfairIdentityService <- ZIO.service[BetfairIdentityService]
    } yield LiveBetfairService(configService, loggerAdapter, client, cache, betfairIdentityService)
  }

class LiveBetfairService(
  appConfigService: AppConfigService,
  loggerAdapter: LoggerAdapter,
  client: Ref[BetfairClient],
  cache: Ref[Map[String, LocalMarketCatalog]],
  betfairIdentityService: BetfairIdentityService
) extends BetfairService:

  implicit val _: Logger = LoggerFactory.getLogger(getClass)

  def getRunner(marketId: String, runnerId: Long): RIO[Clock, Option[LocalRunner]] = for {
    localMarketCatalog <- getMarketCatalog(marketId).flatMap(ZIO.fromOption).mapError(rte("No market " + marketId))
    runner              = localMarketCatalog.getRunner(runnerId)
  } yield runner

  def getMarketCatalog(marketId: String): RIO[Clock, Option[LocalMarketCatalog]] = for {
    cache <- cache.get
    catalog = cache.get(marketId)
    maybeCatalog <- if(catalog.isDefined) ZIO.succeed(catalog) else refreshMarketCatalog(marketId)
  } yield maybeCatalog

  def refreshMarketCatalog(marketId: String): RIO[Clock, Option[LocalMarketCatalog]] = for {
    client <- client.get
    creds <- betfairIdentityService.getCredentials
    _ <- loggerAdapter.info(s"Refreshing market $marketId")
    _ <- ZIO.attempt(client.shortcutLogin(creds.payload.token))
    marketFilter <- ZIO.succeed {
      val marketFilter = new MarketFilter()
      marketFilter.setMarketIds(Set(marketId).asJava)
      marketFilter
    }
    marketProjections <- ZIO.succeed(Set(MARKET_DESCRIPTION, EVENT, RUNNER_DESCRIPTION).asJava)
    result <- ZIO.attemptBlocking(client.listMarketCatalogue(marketFilter, marketProjections, null, 1000))
    _ <- ZIO.when(result.getHasError)(loggerAdapter.error("Response from BetfairClient had error flag set, cant find any more info in the response on that yet! Continuing reluctantly..."))
    now <- ZIO.serviceWithZIO[Clock](_.instant)
    maybeCatalog <- ZIO.attempt {

      val catalogs = for (marketCatalogue <- result.getResponse.asScala) yield {

        val eventVenue = marketCatalogue.getEvent.getVenue
        val eventOpen = marketCatalogue.getEvent.getOpenDate
        val marketName = marketCatalogue.getMarketName
        val marketTime = marketCatalogue.getDescription.getMarketTime
//        println(s"$eventVenue (opens $eventOpen), $marketName at $marketTime")

        val runners = for (runner <- marketCatalogue.getRunners.asScala) yield {
          val runnerName = runner.getRunnerName
          val selectionId = runner.getSelectionId
//          println(s"$runnerName $selectionId")
          LocalRunner(selectionId, runnerName, now)
        }

        LocalMarketCatalog(eventVenue, eventOpen.toOffsetDateTime, marketName, marketTime.toOffsetDateTime, runners.toList, now)
      }

      catalogs.headOption
    }
    _ <- ZIO.foreach(maybeCatalog)(c => cache.update(map => map.updated(marketId, c)))
  } yield maybeCatalog

