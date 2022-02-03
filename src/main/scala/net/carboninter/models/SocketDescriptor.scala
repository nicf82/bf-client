package net.carboninter.models

import zio.*
import zio.stream.ZStream

import java.io.{IOException, InputStream, OutputStream}
import java.net.Socket

trait SocketDescriptor:
  def inputStream: ZStream[Any, IOException, Byte]
  def outputStream: ZOutputStream
  def close: UIO[Unit]

case class SSLSocketDescriptor(socket: Socket) extends SocketDescriptor:
  override def inputStream = ZStream.fromInputStream(socket.getInputStream)
  override def outputStream = ZOutputStream.fromOutputStream(socket.getOutputStream)
  override def close: UIO[Unit] = for {
    _ <- ZIO.succeed(socket.getInputStream.close())
    _ <- ZIO.succeed(socket.getOutputStream.close())
    _ <- ZIO.succeed(socket.close())
  } yield ()

