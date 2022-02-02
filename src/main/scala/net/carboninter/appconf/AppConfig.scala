package net.carboninter.appconf

case class AppConfig(betfair: Betfair)
case class Betfair(appKey: String, userName: String, password: String, identityApi: IdentityApi, streamApi: StreamApi)
case class StreamApi(host: String, port: Int)
case class IdentityApi(uri: String)
