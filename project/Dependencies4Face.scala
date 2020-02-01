import sbt._

/**
  * Created by sky
  * Date on 2019/9/6
  * Time at 下午5:27
  */
object Dependencies4Face {
  val jme3Version = "3.2.3-stable"
  val jme3Libs = Seq(
    // https://mvnrepository.com/artifact/org.jmonkeyengine/jme3-core
    "org.jmonkeyengine" % "jme3-core" % jme3Version,
    // https://mvnrepository.com/artifact/org.jmonkeyengine/jme3-desktop
    "org.jmonkeyengine" % "jme3-desktop" % jme3Version,
    // https://mvnrepository.com/artifact/org.jmonkeyengine/jme3-lwjgl
    "org.jmonkeyengine" % "jme3-lwjgl" % jme3Version,
    "org.jmonkeyengine" % "jme3-plugins" % jme3Version,
    "org.jmonkeyengine" % "jme3-blender" % "3.2.0-beta1"
  )
}
