package net.carboninter.betfair

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

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, IOException, InputStream, OutputStream, PipedInputStream, PipedOutputStream}

object BetfairStreamSpec extends DefaultRunnableSpec:

  import FakeBetfairConnection._

  def msgs(s: String*) = s.mkString("", "\r\n", "\r\n").getBytes("UTF-8")

  val test1 = test("Handshake: connect to the socket, receive the connection message, call get credentials, send the authentication message, receive status message") {

    val program = for {
      streamService <- ZIO.service[BetfairStreamService]
      (simulateSocketRcv, readSocketSent, fakeSocket) <- buildFakeSocket

      _ <- simulateSocketRcv(msgs("""{"op":"connection","connectionId":"connection-id"}"""))

      (counter, _, responseStream) <- streamService.open(fakeSocket)

      queue <- ZQueue.unbounded[Take[Throwable, ResponseMessage]]
      fiber <- responseStream.runIntoQueue(queue).fork

      _ <- simulateSocketRcv(msgs("""{"op":"status","id":0,"statusCode":"SUCCESS","connectionClosed":false,"connectionsAvailable":9}"""))

      responses <- queue.take.flatMap(_.done)
      _ <- ZIO.sleep(1.seconds) //This is to give time for the fake socket's out stream to register the change - try making it a ZStream[String] so we can take?
      _ <- fiber.interrupt
      str = readSocketSent().map(_.toChar).mkString
    } yield {
      assert(responses(0))(Assertion.equalTo( ConnectionMessage(None,Some("connection-id")) )) &&
      assertTrue(str == msgs("""{"id":0,"session":"cred-token","appKey":"anyAppKey","op":"authentication"}""").map(_.toChar).mkString) &&
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

class FakeBetfairConnection(socketInQueue: Queue[Byte], out: ByteArrayOutputStream) extends BetfairConnection:

  override def responseStream: ZStream[Any, IOException, Byte] = ZStream.fromQueue(socketInQueue)

  override def requestSink: Sink[Throwable, String, Nothing, Unit] =
    ZSink.fromOutputStream(out).dropLeftover.contramapChunks[String] { stringChunks =>
      for {
        string <- stringChunks
        bytes <- (string+"\r\n").getBytes
      } yield bytes
    }.map(_ => ())

  override def close: UIO[Unit] = ZIO.unit

  def simulateSocketRcv(a: Array[Byte]) = socketInQueue.offerAll(a)
  def readSocketSent() = out.toByteArray


object FakeBetfairConnection:
  def buildFakeSocket = for {
    socketInQueue <- ZQueue.unbounded[Byte]
    out = new ByteArrayOutputStream(1024)
    fss = new FakeBetfairConnection(socketInQueue, out)
  } yield (socketInQueue.offerAll, () => out.toByteArray, fss)