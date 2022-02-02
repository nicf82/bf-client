package net.carboninter

import net.carboninter.services.{BetfairIdentityService, BetfairStreamService}
import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import zio.stream.*
import zio.*

import java.util.concurrent.TimeUnit
import org.slf4j.{Logger, LoggerFactory}

object Runner extends ZIOAppDefault {

  implicit val _: Logger = LoggerFactory.getLogger(getClass)

  val program: ZIO[ZEnv & BetfairStreamService, Throwable, Unit] = for {
    streamService <- ZIO.service[BetfairStreamService]
    (publishQueue, responseStream) <- streamService.stream
    _ <- responseStream.runDrain
  } yield ()

  def run: ZIO[Environment, Any, Any] = program
    .provideSome[Environment](BetfairIdentityService.live, BetfairStreamService.live, LoggerAdapter.live, AppConfigService.live)
}
