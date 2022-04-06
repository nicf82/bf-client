package net.carboninter

import net.carboninter.Main.Environment
import zio.*

trait ErrorAdt
case class SomeError(code: Int) extends ErrorAdt

object Main2 extends ZIOAppDefault:

  def program(failNicely: Boolean): ZIO[Any, ErrorAdt, Unit] = for {
    _ <- ZIO.when(failNicely)(ZIO.fail(SomeError(127)))
    _ <- ZIO.succeed {
      throw new RuntimeException("unchecked exception!")
    }
  } yield ()


  def run: ZIO[Environment, Any, Any] = program(false)
    .catchAll { errorAdt =>
      for {
        _ <- zio.Console.printLineError("There was a checked error")
        _ <- zio.Console.printLineError(errorAdt.toString)
      } yield ()
    }
    .catchAllDefect { throwable =>
      for {
        _ <- zio.Console.printLineError("There was a defect")
        _ <- zio.Console.printLineError(throwable.toString)
      } yield ()
    }
    .catchAllCause { cause =>
      for {
        _ <- zio.Console.printLineError(cause.isDie)
        _ <- zio.Console.printLineError(cause.prettyPrint)
      } yield ()
    }
    .exit
