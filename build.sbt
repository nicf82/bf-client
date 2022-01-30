name := "bf-client"

version := "0.1"

scalaVersion := "2.13.6"

val zioVersion            = "2.0.0-RC1"
val logbackVersion        = "1.2.10"
val logbackEncoderVersion = "6.6"

libraryDependencies += "dev.zio" %% "zio" % zioVersion
libraryDependencies += "dev.zio" %% "zio-streams" % zioVersion

libraryDependencies += "ch.qos.logback" % "logback-core" % logbackVersion
libraryDependencies += "ch.qos.logback" % "logback-classic" % logbackVersion
libraryDependencies += "net.logstash.logback" % "logstash-logback-encoder" % logbackEncoderVersion

