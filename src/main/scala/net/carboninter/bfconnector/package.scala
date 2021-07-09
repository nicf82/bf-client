package net.carboninter

import net.carboninter.models.Credentials
import zio.clock.Clock
import zio.console.Console
import zio.{Has, Ref, UIO, URIO, URLayer, ZIO, ZLayer}

import java.time.Instant

package object bfconnector {
  type BfConnector = Has[BfConnector.Service]

  // Companion object exists to hold service definition and also the live implementation.
  object BfConnector {
    trait Service {
      def credRef: UIO[Ref[Credentials]]
      def refreshCredentials: UIO[Credentials]
    }

    val live: URLayer[Clock with Console, BfConnector] =
      ZLayer.succeed {
        new Service {
          val credRef: UIO[Ref[Credentials]] =
            Ref.make[Credentials](Credentials("not set", Instant.EPOCH))

          override def refreshCredentials =
            ZIO.succeed(Credentials.expiringInSeconds(10))
        }
      }
  }

  def refreshCredentials(): URIO[BfConnector, Credentials] =
    ZIO.accessM(x => x.get.refreshCredentials)

  def getCredRef(): URIO[BfConnector, Ref[Credentials]] =
    ZIO.accessM(x => x.get.credRef)
}
