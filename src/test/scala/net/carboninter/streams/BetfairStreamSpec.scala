package net.carboninter.streams

import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.Credentials.Payload
import net.carboninter.models.{Credentials, SocketDescriptor}
import net.carboninter.services.{BetfairIdentityService, BetfairStreamService}
import swagger.definitions.*
import swagger.definitions.StatusMessage.StatusCode.*
import zio.test.{TestEnvironment, *}
import zio.stream.*
import zio.Duration.*
import zio.*

import java.time.Instant
import zio.Duration.*

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, IOException, InputStream, OutputStream, PipedInputStream, PipedOutputStream}

class FakeSocketDescriptor(socketInQueue: Queue[Byte], out: ByteArrayOutputStream) extends SocketDescriptor:

  override def inputStream: ZStream[Any, IOException, Byte] = ZStream.fromQueue(socketInQueue)
  override def outputStream: ZOutputStream = ZOutputStream.fromOutputStream(out)
  override def close: UIO[Unit] = ZIO.unit

  def simulateSocketRcv(a: Array[Byte]) = socketInQueue.offerAll(a)
  def readSocketSent = out.toByteArray

object FakeSocketDescriptor:
  def buildFakeSocket = for {
    socketInQueue <- ZQueue.unbounded[Byte]
    out = new ByteArrayOutputStream(4096)
  } yield new FakeSocketDescriptor(socketInQueue, out)


object BetfairStreamSpec extends DefaultRunnableSpec:

  import FakeSocketDescriptor._

  def msgs(s: String*) = s.mkString("", "\r\n", "\r\n").getBytes("UTF-8")

  val test1 = test("Handshake: connect to the socket, receive the connection message, call get credentials, send the authentication message, receive status message") {

    val program = for {
      streamService <- ZIO.service[BetfairStreamService]
      counter <- Ref.make(0)
      fakeSocket <- buildFakeSocket

      _ <- fakeSocket.simulateSocketRcv(msgs("""{"op":"connection","connectionId":"connection-id"}"""))

      (_, responseStream) <- streamService.stream(ZManaged.attempt(fakeSocket), counter)

      queue <- ZQueue.unbounded[Take[Throwable, ResponseMessage]]
      fiber <- responseStream.runIntoQueue(queue).fork

      _ <- fakeSocket.simulateSocketRcv(msgs("""{"op":"status","id":0,"statusCode":"SUCCESS","connectionClosed":false,"connectionsAvailable":9}"""))

      responses <- queue.take.flatMap(_.done)
      _ <- fiber.interrupt

    } yield {
      assert(responses(0))(Assertion.equalTo( ConnectionMessage(None,Some("connection-id")) )) &&
      assertTrue(fakeSocket.readSocketSent == msgs("""{"id":0,"session":"cred-token","appKey":"irEfG0vsZZ64hbvt","op":"authentication"}""")) &&
      assert(responses(1))(Assertion.equalTo( StatusMessage(Some(0),Some(9),None,None,None,Some(false),Some(Success)) ))
    }

    program.provideSomeLayer[ZEnv & AppConfigService & LoggerAdapter & BetfairIdentityService](BetfairStreamService.live)
  }

  val mockBetfairIdentityService: URLayer[Clock, BetfairIdentityService] = ZLayer.succeed(new BetfairIdentityService{
    def getCredentials: RIO[Clock, Credentials] = for {
      now <- Clock.instant
      credentials <- ZIO.succeed(Credentials(Payload("cred-token", "app-key", "SUCCESS", ""), now.plus(5.minutes)))
    } yield credentials
  })

  override val spec = suite(getClass.getCanonicalName)(test1).provide(
    liveEnvironment, LoggerAdapter.live, AppConfigService.live, mockBetfairIdentityService
  )

