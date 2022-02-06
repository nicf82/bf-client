package net.carboninter.betfair

import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.betfair.BetfairStreamSpec.{getClass, mockBetfairIdentityService, suite, test1}
import zio.*
import zio.test.*

object BetfairServiceTest extends DefaultRunnableSpec:

  val test1 = test("List market catalog - FIXME - calls live service, so NEEDS creds set") {
    for {
      betfairIdentityService <- ZIO.service[BetfairIdentityService]
      betfairService <- ZIO.service[BetfairService]
      config <- ZIO.serviceWithZIO[AppConfigService](_.getAppConfig)
      creds <- betfairIdentityService.getCredentials
      r <- betfairService.getMarketCatalog("1.193602267")
      _ = println(r)
      r1 <- betfairService.getMarketCatalog("1.193602267")
      _ = println(r1)
    } yield assert(r)(Assertion.isSome)
  }

  override val spec = suite(getClass.getCanonicalName)(test1).provide(
    liveEnvironment, LoggerAdapter.live, AppConfigService.live, BetfairIdentityService.live, BetfairService.live
  )