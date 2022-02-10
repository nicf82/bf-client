package net.carboninter.appconf

case class AppConfig(betfair: Betfair, kafka: Kafka)

case class Kafka(bootstrapServers: String, groupId: String, topics: Topics)

case class Topics(marketChangeMessageDeltasTopic: Topic, marketChangesTopic: Topic, commandsTopic: Topic)
case class Topic(name: String, partitions: Int, replication: Short, subscribe: Boolean, config: Option[Map[String,String]])

case class Betfair(appKey: String, userName: String, password: String, heartbeatRemote: Int, identityApi: IdentityApi, streamApi: StreamApi)
case class StreamApi(host: String, port: Int)
case class IdentityApi(uri: String)
