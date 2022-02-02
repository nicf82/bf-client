package net.carboninter

import net.carboninter.services.BetfairIdentityService
import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import zio.stream.*
import zio.*

import java.util.concurrent.TimeUnit
import org.slf4j.{Logger, LoggerFactory}

object Runner extends ZIOAppDefault {

  implicit val _: Logger = LoggerFactory.getLogger(getClass)

  val stream: ZStream[Environment with BetfairIdentityService with LoggerAdapter with AppConfigService, Throwable, Unit] =
    ZStream.tick(Duration(5, TimeUnit.SECONDS))
      .mapZIO { _ =>
        ZIO.serviceWithZIO[BetfairIdentityService](_.getCredentials)
      }
      .mapZIO { credentials =>
        LoggerAdapter.info(credentials.toString)
      }

  val program: ZIO[Environment with BetfairIdentityService with LoggerAdapter with AppConfigService, Throwable, Long] = stream.runCount

//  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] = program
  def run: ZIO[Environment, Any, Any] = program
    //.provideLayer(Clock.live ++ Console.live ++ BfConnector.live ++ LoggerAdapter.live)  //Also works
    .provideSome[Environment](BetfairIdentityService.live, LoggerAdapter.live, AppConfigService.live)
}
