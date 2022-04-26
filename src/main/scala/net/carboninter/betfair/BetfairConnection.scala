package net.carboninter.betfair

import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.*
import org.slf4j.{Logger, LoggerFactory}
import swagger.definitions.RequestMessage
import zio.*
import zio.stream.{Sink, ZSink, ZStream}

import java.io.{IOException, InputStream, OutputStream}
import javax.net.ssl.{SSLSocket, SSLSocketFactory}

trait BetfairConnection:
  def responseStream: ZStream[Any, IOException, Byte]
  def requestSink: Sink[Throwable, String, Nothing, Unit]
  def close: UIO[Unit]


object BetfairConnection:

  private val sslSocketFactory = SSLSocketFactory.getDefault
  implicit val _: Logger = LoggerFactory.getLogger(getClass)

//  val live: RLayer[AppConfigService & LoggerAdapter, BConnection] = {
//    def acquire: ZIO[AppConfigService & LoggerAdapter, Throwable, BConnection] = ???
//    def release(s: BConnection): ZIO[AppConfigService & LoggerAdapter, Nothing, Unit] = ???
//    ZManaged.acquireReleaseWith(acquire)(release)
//  }.toLayer


  val live: RLayer[AppConfigService & LoggerAdapter, BetfairConnection] = ZLayer.fromZIO {
    def acquire: ZIO[AppConfigService & LoggerAdapter, Throwable, BetfairConnection] = for {
      config <- ZIO.serviceWithZIO[AppConfigService](_.getAppConfig.map(_.betfair))
      socket <- ZIO.attempt {
        val socket = sslSocketFactory.createSocket(config.streamApi.host, config.streamApi.port).asInstanceOf[SSLSocket]
        socket.startHandshake()
        socket.setReceiveBufferSize(1024 * 1000 * 2) //shaves about 20s off firehose image.
        socket.setSoTimeout(30*1000)
        socket
      }
    } yield LiveBetfairConnection(socket)

    def release(s: BetfairConnection): ZIO[AppConfigService & LoggerAdapter, Nothing, Unit] = for {
      _ <- ZIO.serviceWithZIO[LoggerAdapter](_.info("Closing betfair stream socket"))
      _ <- s.close
    } yield ()

//    ZManaged.acquireReleaseWith(acquire)(release)

    ZIO.scoped(ZIO.acquireRelease(acquire)(release))

  }


case class LiveBetfairConnection(socket: SSLSocket) extends BetfairConnection:
  override def responseStream = ZStream.fromInputStream(socket.getInputStream)

  override def requestSink: Sink[Throwable, String, Nothing, Unit] =
    ZSink.fromOutputStream(socket.getOutputStream).dropLeftover.contramapChunks[String] { stringChunks =>
      for {
        string <- stringChunks
        bytes <- (string+"\r\n").getBytes
      } yield bytes
    }.map(_ => ())

  override def close: UIO[Unit] = ZIO.unit
//    for {
//      _ <- ZIO.succeed(socket.getInputStream.close())
//      _ <- ZIO.succeed(socket.getOutputStream.close())
//      _ <- ZIO.succeed(socket.close())
//    } yield ()

