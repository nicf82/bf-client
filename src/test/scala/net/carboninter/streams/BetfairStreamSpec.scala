package net.carboninter.streams

import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.{Credentials, SocketDescriptor}
import net.carboninter.services.{BetfairIdentityService, BetfairStreamService}
import swagger.definitions.{ConnectionMessage, ResponseMessage}
import zio.test.{TestEnvironment, *}
import zio.stream.*
import zio.*

import java.time.Instant
import zio.Duration.*

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream, PipedInputStream, PipedOutputStream}

object BetfairStreamSpec extends DefaultRunnableSpec:

  val test1 = test("connect to the socket and receive the connect message") {

    val out = new ByteArrayOutputStream(256)
    val in = new ByteArrayInputStream(("""{"op":"connection","connectionId":"100-020222165127-38319"}""" + "\r\n").getBytes("UTF-8"))

    val managedSocketDescriptor: TaskManaged[SocketDescriptor] = ZManaged.attempt {
      new SocketDescriptor {
        override def inputStream: InputStream = in
        override def outputStream: OutputStream = out
        override def close: UIO[Unit] = ZIO.unit
      }
    }

    val program: ZIO[ZEnv & BetfairStreamService, Throwable, ResponseMessage] = for {
      streamService <- ZIO.service[BetfairStreamService]
      (publishQueue, responseStream) <- streamService.stream(managedSocketDescriptor)
      head <- responseStream.runHead
    } yield head.get

    val result = program.provideSomeLayer[ZEnv & AppConfigService & LoggerAdapter & BetfairIdentityService](BetfairStreamService.live)

    assertM(result)(Assertion.equalTo( ConnectionMessage(None,Some("100-020222165127-38319")) ))
  }


  override val spec = suite(getClass.getCanonicalName)(test1).provide(
    liveEnvironment, LoggerAdapter.live, AppConfigService.live, BetfairIdentityService.live
  )

