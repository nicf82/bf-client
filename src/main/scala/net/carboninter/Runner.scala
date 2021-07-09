package net.carboninter

import net.carboninter.models.Credentials
import zio.duration._
import zio.stream._

import zio._
import java.time.Instant
import java.util.concurrent.TimeUnit

object Runner extends App {

  import zio.clock._
  import logging._
  import bfconnector._
  import zio.console._

  val credRef: UIO[Ref[Credentials]] = Ref.make[Credentials](Credentials("not set", Instant.EPOCH))

  val stream = ZStream
    .fromEffect(getCredRef())
    .flatMap { ref =>
      ZStream.tick(Duration(1, TimeUnit.SECONDS)).map(_ => ref)
    }
    .mapM(ref => instant.zip(ZIO.succeed(ref)))
    .mapM { case (now, ref) =>

      for {
        creds <- ref.get
        z     <- if (now.isBefore(creds.expires)) ZIO.succeed(creds)
        else for {
          _  <- log("EXPIRED! Getting new creds")
          nc <- refreshCredentials()
          c  <- ref.set(nc).as(nc)
        } yield c
      } yield z
    }
    .mapM { creds: Credentials =>
      putStrLn(creds.toString)
    }

  override def run(args: List[String]) = {

    stream
      .provideSomeLayer(BfConnector.live ++ Logging.live)
      //.provideLayer(Clock.live ++ Console.live ++ BfConnector.live ++ Logging.live)  //Also works
      .runCount
      .exitCode
  }
}
