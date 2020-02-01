import sbt._

/**
  * Created by sky
  * Date on 2019/7/15
  * Time at 下午2:07
  */
object Dependencies4WebRtc {

  val backendDependencies =
    Dependencies.akkaSeq ++
      Dependencies.akkaHttpSeq ++
      Dependencies.circeSeq ++
      Seq(
        Dependencies.scalaXml,
        Dependencies.slick,
        Dependencies.slickCodeGen,
        Dependencies.nscalaTime,
        Dependencies.hikariCP,
        Dependencies.logback,
        Dependencies.codec,
        Dependencies.postgresql,
        Dependencies.ehcache,
        Dependencies.byteobject
      )

  val kurentoVersion = "6.10.0"
  val kurentoLibs = Seq(
    "org.kurento" % "kurento-client" % kurentoVersion,
    "org.kurento" % "kurento-java" % kurentoVersion
  )

  val dependencies4WebRtc = kurentoLibs ++ Seq(
    "ch.megard" %% "akka-http-cors" % "0.4.0"
  )
}
