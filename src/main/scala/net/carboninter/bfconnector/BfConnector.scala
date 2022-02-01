package net.carboninter.bfconnector

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

trait BfConnector {
  def getCredentials: ZIO[Clock, Throwable, Credentials]
}

object BfConnector {

  val live: URLayer[AppConfigService with LoggerAdapter, BfConnector] = ZLayer.fromZIO {
    for {
      ref           <- Ref.Synchronized.make(Credentials.empty)
      loggerAdapter <- ZIO.service[LoggerAdapter]
      configService <- ZIO.service[AppConfigService]
    } yield LiveBfConnector(ref, configService, loggerAdapter)
  }

}

case class LiveBfConnector(ref: Ref.Synchronized[Credentials], appConfigService: AppConfigService, loggerAdapter: LoggerAdapter) extends BfConnector {

  implicit val _: Logger = LoggerFactory.getLogger(getClass)
  val backend: SttpBackend[Future, Any] = AsyncHttpClientFutureBackend()

  override val getCredentials: ZIO[Clock, Throwable, Credentials] = for {
    now         <- Clock.instant
    credentials <- ref.updateSomeAndGetZIO {
      case credentials if credentials.expires.isBefore(now) => fetchCredentials
    }
  } yield credentials

  private val fetchCredentials: ZIO[Clock, Throwable, Credentials] = for {
    _ <- loggerAdapter.debug("Fetching new credentials")
    conf <- appConfigService.getAppConfig.map(_.betfair)
    uri <- ZIO.attempt(sttp.model.Uri.unsafeParse("https://identitysso.betfair.com/api/login"))
    headers = Header("Accept", "application/json") ::
                Header("X-Application", conf.appKey) ::
                Header("Content-Type", "application/x-www-form-urlencoded") ::
                uri.host.toList.map(h => Header("Host", h))
    req <- ZIO.attempt  {
                          basicRequest.headers(headers:_*)
                            .body(s"username=${conf.userName}&password=${conf.password}")
                            .post(uri)
                        }
    _   <- ZIO.foreach(req.headers)(h => loggerAdapter.debug(h.toString))
    res <- ZIO.fromFuture(implicit ec => req.send(backend))
    _ <- loggerAdapter.debug(res.toString)
    body <-  ZIO.fromEither(res.body).mapError(new RuntimeException(_))
    _ <- loggerAdapter.debug(body)
    credentialsP <- ZIO.fromEither(parser.decode[Credentials.Payload](body)).mapError(new RuntimeException(_))
    now <- Clock.instant
    credentials = Credentials(credentialsP, now.plusSeconds(300))
    _ <- loggerAdapter.error(credentials.toString)
  } yield credentials

}