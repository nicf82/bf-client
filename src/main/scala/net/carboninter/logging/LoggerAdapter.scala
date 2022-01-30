package net.carboninter.logging

import org.slf4j.{Logger, LoggerFactory}
import zio.*

trait LoggerAdapter {

  def trace(string: String)(implicit logger: Logger): IO[Nothing, Unit]

  def debug(string: String)(implicit logger: Logger): IO[Nothing, Unit]

  def info(string: String)(implicit logger: Logger): IO[Nothing, Unit]

  def warn(string: String)(implicit logger: Logger): IO[Nothing, Unit]

  def error(string: String)(implicit logger: Logger): IO[Nothing, Unit]

  def trace(string: String, throwable: Throwable)(implicit logger: Logger): IO[Nothing, Unit]

  def debug(string: String, throwable: Throwable)(implicit logger: Logger): IO[Nothing, Unit]

  def info(string: String, throwable: Throwable)(implicit logger: Logger): IO[Nothing, Unit]

  def warn(string: String, throwable: Throwable)(implicit logger: Logger): IO[Nothing, Unit]

  def error(string: String, throwable: Throwable)(implicit logger: Logger): IO[Nothing, Unit]
}

object LoggerAdapter {
  val live: ULayer[LoggerAdapter] = ZLayer.succeed(LiveLoggerAdapter())

  def trace(string: String)(implicit logger: Logger): URIO[LoggerAdapter, Unit] =
    ZIO.serviceWithZIO(_.trace(string))

  def debug(string: String)(implicit logger: Logger): URIO[LoggerAdapter, Unit] =
    ZIO.serviceWithZIO(_.debug(string))

  def info(string: String)(implicit logger: Logger): URIO[LoggerAdapter, Unit] =
    ZIO.serviceWithZIO(_.info(string))

  def warn(string: String)(implicit logger: Logger): URIO[LoggerAdapter, Unit] =
    ZIO.serviceWithZIO(_.warn(string))

  def error(string: String)(implicit logger: Logger): URIO[LoggerAdapter, Unit] =
    ZIO.serviceWithZIO(_.error(string))

  def trace(string: String, throwable: Throwable)(implicit logger: Logger): URIO[LoggerAdapter, Unit] =
    ZIO.serviceWithZIO(_.trace(string, throwable))

  def debug(string: String, throwable: Throwable)(implicit logger: Logger): URIO[LoggerAdapter, Unit] =
    ZIO.serviceWithZIO(_.debug(string, throwable))

  def info(string: String, throwable: Throwable)(implicit logger: Logger): URIO[LoggerAdapter, Unit] =
    ZIO.serviceWithZIO(_.info(string, throwable))

  def warn(string: String, throwable: Throwable)(implicit logger: Logger): URIO[LoggerAdapter, Unit] =
    ZIO.serviceWithZIO(_.warn(string, throwable))

  def error(string: String, throwable: Throwable)(implicit logger: Logger): URIO[LoggerAdapter, Unit] =
    ZIO.serviceWithZIO(_.error(string, throwable))
}

case class LiveLoggerAdapter() extends LoggerAdapter {

  def log(f: String => Unit)(string: String)(implicit logger: Logger): IO[Nothing, Unit] =
    ZIO.succeed(f(string))

  def log(f: (String, Throwable) => Unit)(string: String, throwable: Throwable)(implicit logger: Logger): IO[Nothing, Unit] =
    ZIO.succeed(f(string, throwable))

  def trace(string: String)(implicit logger: Logger): IO[Nothing, Unit] = log(logger.trace(_))(string)
  def debug(string: String)(implicit logger: Logger): IO[Nothing, Unit] = log(logger.debug(_))(string)
  def info(string: String)(implicit logger: Logger): IO[Nothing, Unit] = log(logger.info(_))(string)
  def warn(string: String)(implicit logger: Logger): IO[Nothing, Unit] = log(logger.warn(_))(string)
  def error(string: String)(implicit logger: Logger): IO[Nothing, Unit] = log(logger.error(_))(string)

  def trace(string: String, throwable: Throwable)(implicit logger: Logger): IO[Nothing, Unit] = log(logger.trace(_: String, _: Throwable))(string, throwable)
  def debug(string: String, throwable: Throwable)(implicit logger: Logger): IO[Nothing, Unit] = log(logger.debug(_: String, _: Throwable))(string, throwable)
  def info(string: String, throwable: Throwable)(implicit logger: Logger): IO[Nothing, Unit] = log(logger.info(_: String, _: Throwable))(string, throwable)
  def warn(string: String, throwable: Throwable)(implicit logger: Logger): IO[Nothing, Unit] = log(logger.warn(_: String, _: Throwable))(string, throwable)
  def error(string: String, throwable: Throwable)(implicit logger: Logger): IO[Nothing, Unit] = log(logger.error(_: String, _: Throwable))(string, throwable)

}