import sbt._

/**
  * User: Arrow
  * Date: 2019/7/16
  * Time: 16:18
  */
object Dependencies4PcClient {

  val akkaV = "2.5.23"
  val akkaHttpV = "10.1.8"
  val circeVersion = "0.9.3"


  val akkaSeq = Seq(
    //    "com.typesafe.akka" %% "akka-actor" % akkaV withSources(),
    "com.typesafe.akka" %% "akka-actor-typed" % akkaV withSources(),
    "com.typesafe.akka" %% "akka-slf4j" % akkaV,
    //    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-stream-typed" % akkaV
  )

  val akkaHttpSeq = Seq(
    "com.typesafe.akka" %% "akka-http" % akkaHttpV
    //    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV
  )

  val circeSeq = Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion
  )
  
  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val asynchttpclient = "org.asynchttpclient" % "async-http-client" % "2.0.32"
  val byteobject = "org.seekloud" %% "byteobject" % "0.1.1"
  //  val sigar =  "org.fusesource" % "sigar" % "1.6.4"
  val oshi = "com.github.oshi" % "oshi-core" % "4.0.0"


  val pcClientDependencies: Seq[ModuleID] =
    akkaSeq ++ akkaHttpSeq ++ circeSeq ++
    Seq(
      logback,
      asynchttpclient,
      byteobject,
      //      sigar,
      oshi
    )


}
