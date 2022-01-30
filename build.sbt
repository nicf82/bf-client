name := "bf-client"

version := "0.1"

scalaVersion := "3.1.1"

val zioVersion             = "2.0.0-RC1"
val zioConfigVersion       = "3.0.0-RC1"
val logbackVersion         = "1.2.10"
val logbackEncoderVersion  = "6.6"
val asyncHttpClientVersion = "3.3.18"
val zioJsonVersion         = "0.3.0-RC2"

libraryDependencies += "dev.zio" %% "zio"                  % zioVersion
libraryDependencies += "dev.zio" %% "zio-streams"          % zioVersion
libraryDependencies += "dev.zio" %% "zio-json"             % zioJsonVersion
libraryDependencies += "dev.zio" %% "zio-config"           % zioConfigVersion
libraryDependencies += "dev.zio" %% "zio-config-magnolia"  % zioConfigVersion
libraryDependencies += "dev.zio" %% "zio-config-typesafe"  % zioConfigVersion

libraryDependencies += "com.softwaremill.sttp.client3" %%
  "async-http-client-backend-future" % asyncHttpClientVersion

libraryDependencies += "ch.qos.logback"       % "logback-core"             % logbackVersion
libraryDependencies += "ch.qos.logback"       % "logback-classic"          % logbackVersion
libraryDependencies += "net.logstash.logback" % "logstash-logback-encoder" % logbackEncoderVersion

