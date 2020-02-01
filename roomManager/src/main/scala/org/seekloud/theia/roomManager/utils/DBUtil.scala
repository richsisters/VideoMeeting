package org.seekloud.VideoMeeting.roomManager.utils

import com.zaxxer.hikari.HikariDataSource
import org.seekloud.VideoMeeting.roomManager.common.AppSettings._
import org.slf4j.LoggerFactory
import slick.jdbc.H2Profile

/**
 * User: Taoz
 * Date: 2/9/2015
 * Time: 4:33 PM
 */
object DBUtil {

  val log = LoggerFactory.getLogger(this.getClass)
  private val dataSource = createDataSource()

  private def createDataSource() = {

    val dataSource = new org.postgresql.ds.PGSimpleDataSource()
    dataSource.setUrl(slickUrl)
    dataSource.setUser(slickUser)
    dataSource.setPassword(slickPassword)
    val hikariDS = new HikariDataSource()
    hikariDS.setDataSource(dataSource)
    hikariDS.setMaximumPoolSize(slickMaximumPoolSize)
    hikariDS.setConnectionTimeout(slickConnectTimeout)
    hikariDS.setIdleTimeout(slickIdleTimeout)
    hikariDS.setMaxLifetime(slickMaxLifetime)
    hikariDS.setAutoCommit(true)
    hikariDS
  }

  val driver = H2Profile

  import driver.api.Database

  val db: Database = Database.forDataSource(dataSource, Some(slickMaximumPoolSize))

}