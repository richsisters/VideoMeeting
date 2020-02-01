logLevel := Level.Warn



// `javacpp` are packaged with maven-plugin packaging, we need to make SBT aware that it should be added to class path.
classpathTypes += "maven-plugin"

// javacpp `Loader` is used to determine `platform` classifier in the project`s `build.sbt`
// We define dependency here (in folder `project`) since it is used by the build itself.
libraryDependencies += "org.bytedeco" % "javacpp" % "1.5"

val sbtRevolverV = "0.9.1"
val sbtPackV = "0.12"
val sbtScalaJsV = "0.6.28"

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.1")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % sbtScalaJsV)

addSbtPlugin("io.spray" % "sbt-revolver" % sbtRevolverV)

addSbtPlugin("org.xerial.sbt" % "sbt-pack" % sbtPackV)


