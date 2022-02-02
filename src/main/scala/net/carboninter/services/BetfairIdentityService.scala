package net.carboninter.services

import net.carboninter.appconf.AppConfigService
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.Credentials
import org.slf4j.*
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client3.{SttpBackend, basicRequest}
import sttp.model.Header
import zio.*
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.concurrent.Future

trait BetfairIdentityService {
  def getCredentials: Task[Credentials]
}

object BetfairIdentityService {

  val live: URLayer[Clock & AppConfigService & LoggerAdapter, BetfairIdentityService] = ZLayer.fromZIO {
    for {
      ref           <- Ref.Synchronized.make(Credentials.empty)
      clock         <- ZIO.service[Clock]
      loggerAdapter <- ZIO.service[LoggerAdapter]
      configService <- ZIO.service[AppConfigService]
    } yield LiveBetfairIdentityService(ref, clock, configService, loggerAdapter)
  }

}

case class LiveBetfairIdentityService(ref: Ref.Synchronized[Credentials], clock: Clock, appConfigService: AppConfigService, loggerAdapter: LoggerAdapter) extends BetfairIdentityService {

  implicit val _: Logger = LoggerFactory.getLogger(getClass)
  val backend: SttpBackend[Future, Any] = AsyncHttpClientFutureBackend()

  override val getCredentials: Task[Credentials] = for {
    now         <- clock.instant
    credentials <- ref.updateSomeAndGetZIO {
      case credentials if credentials.expires.isBefore(now) => fetchCredentials
    }
    _ <- loggerAdapter.warn("Clock is at " + now + " creds expire at " + credentials.expires)
  } yield credentials

  private val fetchCredentials: Task[Credentials] = for {
    _            <- loggerAdapter.debug("Fetching new credentials")
    conf         <- appConfigService.getAppConfig.map(_.betfair)
    uri          <- ZIO.attempt(sttp.model.Uri.unsafeParse("https://identitysso.betfair.com/api/login"))
    headers       = Header("Accept", "application/json") ::
                      Header("X-Application", conf.appKey) ::
                      Header("Content-Type", "application/x-www-form-urlencoded") ::
                      uri.host.toList.map(h => Header("Host", h))
    req          <- ZIO.attempt  {
                      basicRequest.headers(headers:_*)
                        .body(s"username=${conf.userName}&password=${conf.password}")
                        .post(uri)
                    }
    _            <- ZIO.foreach(req.headers)(h => loggerAdapter.trace(h.toString))
    res          <- ZIO.fromFuture(implicit ec => req.send(backend))
    body         <-  ZIO.fromEither(res.body).mapError(new RuntimeException(_))
    _            <- loggerAdapter.trace(body)
    credentialsP <- ZIO.fromEither(parser.decode[Credentials.Payload](body)).mapError(new RuntimeException(_))
    now          <- clock.instant
    credentials   = Credentials(credentialsP, now.plusSeconds(300))
    _            <- loggerAdapter.debug(credentials.toString)
  } yield credentials

}