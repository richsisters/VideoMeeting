package org.seekloud.VideoMeeting.roomManager.utils

import slick.codegen.SourceCodeGenerator
import slick.jdbc.{H2Profile, JdbcProfile}
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * User: Taoz
  * Date: 7/15/2015
  * Time: 9:33 AM
  */
object MySlickCodeGenerator {

  import concurrent.ExecutionContext.Implicits.global

  val slickProfile = "slick.jdbc.H2Profile"
  val jdbcDriver = "org.h2.Driver"
  val url = "jdbc:h2:file:./data/VideoMeeting"
  val outputFolder = "target/gencode/genTablesPsql"
  val pkg = "org.seekloud.VideoMeeting.roomManager.models"
  val user = "VideoMeeting"
  val password = "1qaz@WSX"//Rm1qaz@WSX

  def genCustomTables(dbProfile: JdbcProfile) = {

    // fetch data model
    val profile: JdbcProfile =
      Class.forName(slickProfile + "$").getField("MODULE$").get(null).asInstanceOf[JdbcProfile]
    val dbFactory = profile.api.Database
    val db = dbFactory.forURL(url, driver = jdbcDriver,
      user = user, password = password, keepAliveConnection = true)

    // fetch data model
    val modelAction = dbProfile.createModel(Some(dbProfile.defaultTables))
    // you can filter specific tables here
    val modelFuture = db.run(modelAction)

    // customize code generator
    val codeGenFuture = modelFuture.map(model => new SourceCodeGenerator(model) {
      // override mapped table and class name
      override def entityName =
        dbTableName => "r" + dbTableName.toCamelCase

      override def tableName =
        dbTableName => "t" + dbTableName.toCamelCase

      // add some custom import
      // override def code = "import foo.{MyCustomType,MyCustomTypeMapper}" + "\n" + super.code

      // override table generator
      /*    override def Table = new Table(_){
            // disable entity class generation and mapping
            override def EntityType = new EntityType{
              override def classEnabled = false
            }

            // override contained column generator
            override def Column = new Column(_){
              // use the data model member of this column to change the Scala type,
              // e.g. to a custom enum or anything else
              override def rawType =
                if(model.name == "SOME_SPECIAL_COLUMN_NAME") "MyCustomType" else super.rawType
            }
          }*/
    })

    val codeGenerator = Await.result(codeGenFuture, Duration.Inf)
    codeGenerator.writeToFile(
      slickProfile, outputFolder, pkg, "SlickTables", "SlickTables.scala"
    )

  }


  def genDefaultTables() = {

    slick.codegen.SourceCodeGenerator.main(
      Array(slickProfile, jdbcDriver, url, outputFolder, pkg, user, password)
    )

  }


  def main(args: Array[String]) {
    //genDefaultTables()
    val dbProfile = H2Profile

    genCustomTables(dbProfile)

    println(s"Tables.scala generated in $outputFolder")

  }

}


