package net.carboninter.appconf

import zio.*
import zio.config.*
import zio.config.magnolia.*
import zio.config.typesafe.*

trait AppConfigService {
  def getAppConfig: ZIO[Any, Nothing, AppConfig]
}

object AppConfigService {
  val live: ULayer[AppConfigService] = ZLayer.succeed(LiveAppConfigService())
}

case class LiveAppConfigService() extends AppConfigService {

  private val configDescription: ConfigDescriptor[AppConfig] = descriptor[AppConfig]

  override def getAppConfig: ZIO[Any, Nothing, AppConfig] = (for {
    config <- ZIO.service[AppConfig]
  } yield config).provide(TypesafeConfig.fromResourcePath(configDescription).orDie)
}

//class TestAppConfigService(applyOverrides: AppConfig => AppConfig) extends AppConfigService {
//
//  private val configDescription: ConfigDescriptor[AppConfig] = descriptor[AppConfig]
//
//  override def getAppConfig: ZIO[Any, Nothing, AppConfig] = (for {
//    config <- ZIO.service[AppConfig]
//    c = applyOverrides(config)
//  } yield c).provide(TypesafeConfig.fromResourcePath(configDescription).orDie)
//}
