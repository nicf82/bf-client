package net.carboninter.models

import zio.*

import java.io.{InputStream, OutputStream}
import java.net.Socket

trait SocketDescriptor:
  def inputStream: InputStream
  def outputStream: OutputStream
  def close: UIO[Unit]

case class SSLSocketDescriptor(socket: Socket) extends SocketDescriptor:
  override def inputStream = socket.getInputStream
  override def outputStream = socket.getOutputStream
  override def close: UIO[Unit] = for {
    _ <- ZIO.succeed(socket.getInputStream.close())
    _ <- ZIO.succeed(socket.getOutputStream.close())
    _ <- ZIO.succeed(socket.close())
  } yield ()

