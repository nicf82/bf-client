package net.carboninter.models

import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.Credentials.Payload
import net.carboninter.models.Credentials
import net.carboninter.betfair.{BetfairIdentityService, BetfairStreamService}
import swagger.definitions.*
import swagger.definitions.StatusMessage.StatusCode.*
import zio.test.{TestEnvironment, *}
import zio.stream.*
import zio.Duration.*
import zio.*

import java.time.Instant
import zio.Duration.*
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*

import java.io.File
import java.net.URL
import scala.io.Source
import cats.*
import cats.implicits.*

object ModelSpec  extends DefaultRunnableSpec:



  def parseTest(file: File) = test("Successfully decode test response in: " + file.getName) {

    val raw = Source.fromFile(file).mkString
    val result = decode[ResponseMessage](raw)

    assert(result.swap.map(_.show).swap)(Assertion.isRight)
  }

  lazy val dir = new File(getClass.getResource(s"/testResponses").getFile)
  lazy val files = dir.listFiles()

  override lazy val spec = suite(getClass.getCanonicalName)(files.map(parseTest): _*).provide(
    liveEnvironment
  )