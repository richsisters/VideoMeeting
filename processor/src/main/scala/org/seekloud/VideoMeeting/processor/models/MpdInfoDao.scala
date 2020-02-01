package org.seekloud.VideoMeeting.processor.models

import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.processor.utils.DBUtil.db
import scala.concurrent.Future
/**
  * Author: lenovo
  * Date: 2019-09-05
  * Time: 15:31
  */
object MpdInfoDao extends SlickTables{

  private val log = LoggerFactory.getLogger(this.getClass)
  val profile = slick.jdbc.PostgresProfile
  import profile.api._

  def getMpd4Record(roomId:Long, startTime:Long):Future[String] = {
    try {
      db.run(tMpdInfo.filter(t => (t.roomId === roomId) && (t.startTime === startTime)).map(r => r.mpdAddr).result.head)
    } catch {
      case e: Throwable =>
        log.error(s"getMpd4Record error with error $e")
        Future.successful(" ")
    }
  }

  def getRecordList(roomId:Long):Future[Seq[rMpdInfo]] = {
    try {
      db.run(tMpdInfo.filter(_.roomId === roomId).result)
    } catch {
      case e: Throwable =>
        log.error(s"getRecordList error with error $e")
        Future.successful(Nil)
    }
  }

  def addRecord(roomId: Long, startTime: Long , endTime: Long , mpdAddr: String):Future[Int] = {
    try {
      db.run(tMpdInfo.map(t => (t.roomId, t.startTime ,t.endTime, t.mpdAddr)) += (roomId,startTime,endTime,mpdAddr))
    } catch {
      case e: Throwable =>
        log.error(s"getRecordList error with error $e")
        Future.successful(-1)
    }
  }

  def updateEndTime(roomId: Long, startTime: Long,endTime: Long):Future[Int] = {
    try {
      db.run(tMpdInfo.filter(t => (t.roomId === roomId) && (t.startTime === startTime)).map(r => r.endTime).update(endTime))
    } catch {
      case e: Throwable =>
        log.error(s"getRecordList error with error $e")
        Future.successful(-1)
    }
  }
}
