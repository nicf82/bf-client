package net.carboninter.appconf

case class AppConfig(betfair: Betfair, kafka: Kafka)
case class Kafka(bootstrapServers: String, groupId: String)
case class Betfair(appKey: String, userName: String, password: String, heartbeatRemote: Int, identityApi: IdentityApi, streamApi: StreamApi)
case class StreamApi(host: String, port: Int)
case class IdentityApi(uri: String)
