package net.carboninter

import net.carboninter.bfconnector.BfConnector
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.Credentials
import zio.Clock._
import zio.stream._
import zio._

import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

object Runner extends ZIOAppDefault {

  implicit val logger = LoggerFactory.getLogger(getClass)

  val credRef: UIO[Ref[Credentials]] = Ref.make[Credentials](Credentials("not set", Instant.EPOCH))

  val stream: ZStream[Console with BfConnector with LoggerAdapter with Clock, IOException, Unit] = ZStream.fromZIO(BfConnector.getCredRef())
    .flatMap { ref =>
      ZStream.tick(Duration(1, TimeUnit.SECONDS)).map(_ => ref)
    }
    .mapZIO(ref => instant.zip(ZIO.succeed(ref)))
    .mapZIO { case (now, ref) =>

      for {
        creds <- ref.get
        z     <- if (now.isBefore(creds.expires)) ZIO.succeed(creds)
        else for {
          _  <- LoggerAdapter.info("EXPIRED! Getting new creds")
          nc <- BfConnector.refreshCredentials()
          c  <- ref.set(nc).as(nc)
        } yield c
      } yield z
    }
    .mapZIO { creds: Credentials =>
      LoggerAdapter.info(creds.toString)
    }

  override def run: ZIO[ZEnv with ZIOAppArgs, Any, Any] = stream
    .provideSomeLayer(BfConnector.live ++ LoggerAdapter.live)
    //.provideLayer(Clock.live ++ Console.live ++ BfConnector.live ++ LoggerAdapter.live)  //Also works
    .runCount
    .exitCode
}
