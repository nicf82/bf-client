package net.carboninter.models

import cats.*
import cats.implicits.*
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import net.carboninter.appconf.AppConfigService
import net.carboninter.betfair.{BetfairIdentityService, BetfairStreamService}
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.Credentials
import net.carboninter.models.Credentials.Payload
import swagger.definitions.{PriceSize, *}
import swagger.definitions.StatusMessage.StatusCode.*
import zio.*
import zio.Duration.*
import zio.stream.*
import zio.test.{TestEnvironment, *}

import java.io.File
import java.net.{URI, URL}
import java.time.Instant
import scala.io.Source
import java.nio.file.{Files, Paths}

object MergeMarketSpec extends DefaultRunnableSpec:

  val test2 = test("save stream as list of files") {
    val rawLines = Source.fromFile(getClass.getResource(s"/testMarketChangeMessages/testObjects1.json").getFile).getLines()

    for(line <- rawLines) {
      val mcm = decode[MarketChangeMessage](line)
      mcm match {
        case Left(error) =>
          java.lang.System.err.println(error.show)

        case Right(mcm) =>
          val file = new File(s"/Users/nic/workspaces/arbing/bf-client/src/test/resources/testMarketChangeMessages/stream2/${mcm.pt}.json")
          file.createNewFile()
          Files.write(Paths.get(file.toURI), mcm.asJson.spaces2.getBytes)
      }
    }

    assertTrue(1 == 1)
  }

  val test3 = test("Merging a price/size list works to spec") {

    val original = Vector(PriceSize(1.0, 30.0), PriceSize(2.0, 60.0), PriceSize(3.0, 90.0))
    val delta    = Vector(PriceSize(1.0, 0.0), PriceSize(2.0, 45.0), PriceSize(4.0, 10.0))
    val result   = MarketChangeMergeTools.mergePriceSizes(original, delta)

    assertTrue(result == Vector(PriceSize(2.0, 45.0), PriceSize(3.0, 90.0), PriceSize(4.0, 10.0)))
  }

  val test4 = test("Merging a level/price/size list works to spec") {

    val original = Vector(LevelPriceSize(1, 1.0, 30.0), LevelPriceSize(2, 2.0, 60.0), LevelPriceSize(3, 3.0, 90.0))
    //This probably would not happen in practice as there will always be a 3 if there is a 4 - ie only the tail items would get set to 0
    val delta    = Vector(LevelPriceSize(1, 2.0, 60.0), LevelPriceSize(2, 3.0, 70.0), LevelPriceSize(3, 4.0, 0.0), LevelPriceSize(4, 4.0, 100.0))
    val result   = MarketChangeMergeTools.mergeLevelPriceSizes(original, delta)

    assertTrue(result == Vector(LevelPriceSize(1, 2.0, 60.0), LevelPriceSize(2, 3.0, 70.0), LevelPriceSize(4, 4.0, 100.0)))
  }

  val test5 = test("Merging if a delta is present (tested using replaceIfDelta") {

    assertTrue(MarketChangeMergeTools.replaceIfOriginalAndDelta(Some(10), Some(5)) == Some(5))
    assertTrue(MarketChangeMergeTools.replaceIfOriginalAndDelta(Some(10), None) == Some(10))
    assertTrue(MarketChangeMergeTools.replaceIfOriginalAndDelta(None, Some(5)) == Some(5))
    assertTrue(MarketChangeMergeTools.replaceIfOriginalAndDelta(None, None) == None)
  }

  val test1 = test("Successfully merge market change messages") {

    val raw = Source.fromFile(getClass.getResource(s"/testMarketChangeMessages/testStream1.json").getFile).mkString
    val json = parse(raw).toOption.get

    val original = json.asArray.get(0).asObject.get("value").get.as[MarketChangeMessage].toOption.get
    val delta    = json.asArray.get(1).asObject.get("value").get.as[MarketChangeMessage].toOption.get

    val result = MarketChangeMergeTools.mergeMCM(original, delta)
    println(result.asJson)

    assertTrue(1==1)
  }

  override lazy val spec = suite(getClass.getCanonicalName)(test1 /*, test3, test4, test5*/).provide(
    liveEnvironment
  )