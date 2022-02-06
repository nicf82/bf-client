package net.carboninter.models

import swagger.definitions.RequestMessage
import zio.*
import zio.stream.{Sink, ZSink, ZStream}

import java.io.{IOException, InputStream, OutputStream}
import javax.net.ssl.SSLSocket

trait SocketDescriptor:
  def responseStream: ZStream[Any, IOException, Byte]
  def requestSink: Sink[Throwable, String, Nothing, Unit]
  def close: UIO[Unit]

case class SSLSocketDescriptor(socket: SSLSocket) extends SocketDescriptor:
  override def responseStream = ZStream.fromInputStream(socket.getInputStream)

  override def requestSink: Sink[Throwable, String, Nothing, Unit] =
    ZSink.fromOutputStream(socket.getOutputStream).dropLeftover.contramapChunks[String] { stringChunks =>
      for {
        string <- stringChunks
        bytes <- (string+"\r\n").getBytes
      } yield bytes
    }.map(_ => ())

  override def close: UIO[Unit] =
    for {
      _ <- ZIO.succeed(socket.getInputStream.close())
      _ <- ZIO.succeed(socket.getOutputStream.close())
      _ <- ZIO.succeed(socket.close())
    } yield ()

