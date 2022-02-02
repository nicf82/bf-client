package net.carboninter.streams

import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.Credentials
import net.carboninter.services.{BetfairIdentityService, BetfairStreamService}
import zio.test.{TestEnvironment, *}
import zio.*

import java.time.Instant
import zio.Duration._

object BetfairStreamSpec extends DefaultRunnableSpec:

  val test1 = test("connect to the socket and receive the connect message") {

    val program: ZIO[ZEnv & BetfairStreamService, Throwable, Unit] = for {
      streamService <- ZIO.service[BetfairStreamService]
      _ <- streamService.stream.runDrain
    } yield ()

    val result = program.provideSomeLayer[ZEnv & AppConfigService & LoggerAdapter & BetfairIdentityService](BetfairStreamService.live)

    assertM(result)(Assertion.equalTo("hello"))
  }


  override val spec = suite(getClass.getCanonicalName)(test1).provide(
    liveEnvironment, LoggerAdapter.live, AppConfigService.live, BetfairIdentityService.live
  )

