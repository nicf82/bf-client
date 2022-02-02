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


    val p = ZIO.service[BetfairStreamService]

    val program: ZIO[TestEnvironment & BetfairStreamService, Throwable, Unit] = for {
      streamService <- ZIO.service[BetfairStreamService]
      _ <- TestClock.adjust(60.minutes)
      _ <- streamService.stream.runDrain
    } yield ()

    val result = program.provideSomeLayer[TestEnvironment & AppConfigService & LoggerAdapter & BetfairIdentityService](BetfairStreamService.live)

    assertM(result)(Assertion.equalTo("hello"))
  }


  override val spec = suite(getClass.getCanonicalName)(test1).provideCustom(
    LoggerAdapter.live, AppConfigService.live, BetfairIdentityService.live
  )
//    .provideSomeLayerShared(LoggerAdapter.live)
//    .provideSomeLayerShared(AppConfigService.live)
//    .provideSomeLayerShared[TestEnvironment & LoggerAdapter & AppConfigService](BetfairIdentityService.live)

