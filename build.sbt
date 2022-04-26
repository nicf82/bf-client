resolvers += Resolver.mavenLocal
//resolvers += DefaultMavenRepository

name := "bf-client"

version := "0.1"

scalaVersion := "3.1.2"

val zioVersion             = "2.0.0-RC5"
val zioTestVersion         = "2.0.0-RC5"
val zioConfigVersion       = "3.0.0-RC8"
val logbackVersion         = "1.2.11"
val logbackEncoderVersion  = "7.1.1"
val asyncHttpClientVersion = "3.5.2"
val zioJsonVersion         = "0.3.0-RC7"
val circeVersion           = "0.14.1"

libraryDependencies += "org.apache.kafka" % "kafka-clients"  % "3.1.0"

libraryDependencies += "dev.zio" %% "zio"                  % zioVersion
libraryDependencies += "dev.zio" %% "zio-streams"          % zioVersion
libraryDependencies += "dev.zio" %% "zio-config"           % zioConfigVersion
libraryDependencies += "dev.zio" %% "zio-config-magnolia"  % zioConfigVersion
libraryDependencies += "dev.zio" %% "zio-config-typesafe"  % zioConfigVersion

libraryDependencies += "io.circe" %% "circe-core"          % circeVersion
libraryDependencies += "io.circe" %% "circe-generic"       % circeVersion
libraryDependencies += "io.circe" %% "circe-parser"        % circeVersion

libraryDependencies += "com.softwaremill.sttp.client3" %%
  "async-http-client-backend-future" % asyncHttpClientVersion

libraryDependencies += "ch.qos.logback"       % "logback-core"             % logbackVersion
libraryDependencies += "ch.qos.logback"       % "logback-classic"          % logbackVersion
libraryDependencies += "net.logstash.logback" % "logstash-logback-encoder" % logbackEncoderVersion

libraryDependencies += "dev.zio" %% "zio-test"             % zioTestVersion % Test
libraryDependencies += "dev.zio" %% "zio-test-sbt"         % zioTestVersion % Test

testFrameworks += TestFramework("zio.test.sbt.ZTestFramework")

//Commented out because the models generated are not perfect, so they are fixed then copied into src
//Compile / guardrailTasks := List(
//  ScalaModels(file("ESASwaggerSchema.json"))
//)