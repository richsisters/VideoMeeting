package org.seekloud.VideoMeeting.roomManager.utils

import com.zaxxer.hikari.HikariDataSource
import org.seekloud.VideoMeeting.roomManager.common.AppSettings._
import org.slf4j.LoggerFactory
import slick.jdbc.H2Profile.api._

/**
 * User: Taoz
 * Date: 2/9/2015
 * Time: 4:33 PM
 */
object DBUtil {

  val log = LoggerFactory.getLogger(this.getClass)
  private val dataSource = createDataSource()

  private def createDataSource() = {
    val dataSource = new org.h2.jdbcx.JdbcDataSource
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

  val db: Database = Database.forDataSource(dataSource, Some(slickMaximumPoolSize))

}