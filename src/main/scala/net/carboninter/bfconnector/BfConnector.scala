package net.carboninter.bfconnector

import net.carboninter.models.Credentials
import zio.Clock
import zio.Console
import zio.{Ref, UIO, URIO, URLayer, ZIO, ZLayer}

import java.time.Instant

trait BfConnector {
  def credRef: UIO[Ref[Credentials]]
  def refreshCredentials: UIO[Credentials]
}

object BfConnector {

  val live: URLayer[Clock with Console, BfConnector] = ZLayer.succeed(LiveBfConnector())

  def refreshCredentials(): URIO[BfConnector, Credentials] =
    ZIO.environmentWithZIO(x => x.get.refreshCredentials)

  def getCredRef(): URIO[BfConnector, Ref[Credentials]] =
    ZIO.environmentWithZIO(x => x.get.credRef)
}

case class LiveBfConnector() extends BfConnector {

  val credRef: UIO[Ref[Credentials]] =
    Ref.make[Credentials](Credentials("not set", Instant.EPOCH))

  override def refreshCredentials =
    ZIO.succeed(Credentials.expiringInSeconds(10))
}