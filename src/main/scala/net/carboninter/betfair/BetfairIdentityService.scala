package net.carboninter.betfair

import net.carboninter.appconf.{AppConfigService, Betfair}
import net.carboninter.logging.LoggerAdapter
import net.carboninter.models.Credentials
import org.slf4j.*
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client3.{SttpBackend, basicRequest}
import sttp.model.{Header, Uri}
import zio.*
import io.circe.*
import io.circe.syntax.*
import io.circe.generic.auto.*

import scala.concurrent.Future

trait BetfairIdentityService {
  def getCredentials: Task[Credentials]
}

object BetfairIdentityService {

  val live: URLayer[AppConfigService & LoggerAdapter, BetfairIdentityService] = ZLayer.fromZIO {
    for {
      ref           <- Ref.Synchronized.make(Credentials.empty)
      loggerAdapter <- ZIO.service[LoggerAdapter]
      configService <- ZIO.service[AppConfigService]
    } yield LiveBetfairIdentityService(ref, configService, loggerAdapter)
  }

}

case class LiveBetfairIdentityService(ref: Ref.Synchronized[Credentials], appConfigService: AppConfigService, loggerAdapter: LoggerAdapter) extends BetfairIdentityService {

  implicit val _: Logger = LoggerFactory.getLogger(getClass)
  val backend: SttpBackend[Future, Any] = AsyncHttpClientFutureBackend()

  override val getCredentials: Task[Credentials] = for {
    now         <- Clock.instant
    credentials <- ref.updateSomeAndGetZIO {
      case credentials if credentials.expires.isBefore(now) => fetchCredentials
    }
  } yield credentials

  //TODO - implement keep alive within every 12 hours: https://docs.developer.betfair.com/pages/viewpage.action?pageId=3834909#Login&SessionManagement-KeepAlive
  
  private val fetchCredentials: Task[Credentials] = for {
    conf         <- appConfigService.getAppConfig.map(_.betfair)
    body         <- postRequest(conf.identityApi.uri, s"username=${conf.userName}&password=${conf.password}", conf.appKey)
    credentialsP <- ZIO.fromEither(parser.decode[Credentials.Payload](body)).mapError(new RuntimeException(_))
    now          <- Clock.instant
    credentials   = Credentials(credentialsP, now.plusSeconds(900))
    _            <- loggerAdapter.debug(s"Fetched new credentials: token=${credentials.payload.token}, expires=${credentials.expires}")
  } yield credentials

  def postRequest(uri: String, body: String, appKey: String): Task[String] = for {
    headers      <-  ZIO.succeed(List("Accept"-> "application/json", "X-Application"-> appKey,
                          "Content-Type" -> "application/x-www-form-urlencoded").map(Header(_, _)))
    uri          <- ZIO.attempt(sttp.model.Uri.unsafeParse(uri))
    req          <- ZIO.attempt  {
                      basicRequest.headers(headers:_*)
                        .body(body)
                        .post(uri)
                    }
    _            <- ZIO.foreach(req.headers)(h => loggerAdapter.trace(h.toString))
    res          <- ZIO.fromFuture(implicit ec => req.send(backend))
    body         <- ZIO.fromEither(res.body).mapError(new RuntimeException(_))
    _            <- loggerAdapter.trace(body)
  } yield body
}