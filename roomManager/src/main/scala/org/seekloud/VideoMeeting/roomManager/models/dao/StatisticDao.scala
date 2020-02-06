package org.seekloud.VideoMeeting.roomManager.models.dao

import java.util

import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.RecordInfo
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol.GetRecordListRsp
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.StatisticsProtocol.AdminRecordInfo
import org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager.ProcessorProtocol.RecordData
import org.seekloud.VideoMeeting.roomManager.utils.DBUtil._
import org.seekloud.VideoMeeting.roomManager.models.SlickTables._
import slick.jdbc.PostgresProfile.api._
import org.seekloud.VideoMeeting.roomManager.Boot.executor
import org.seekloud.VideoMeeting.roomManager.models.SlickTables

import scala.collection.mutable
import scala.concurrent.Future
/**
  * created by benyafang on 2019/9/26
  * 统计
  * */
object StatisticDao {
  def addLoginEvent(uid:Long, time:Long) = {
    db.run(tLoginEvent += rLoginEvent(1, uid, time))
  }

  def addObserveEvent(uid:Long, rid:Long, anchor:Boolean, temp:Boolean, in_time:Long) = {
    db.run(tObserveEvent += rObserveEvent(1,uid,rid,anchor,temp,in_time))
  }

  def updateObserveEvent(recordId:Long,uid:Long,temporary:Boolean,startTime:Long,overTime:Long) = {
    db.run(tObserveEvent.filter(r => r.recordid === recordId && r.uid === uid && r.temporary === temporary && r.inTime === startTime).map(_.outTime).update(overTime))
  }


  def getLoginNum = {
    db.run(tLoginEvent.size.result)
  }

  def getLoginInDataByTime(startTime:Long,endTime:Long) = {
    db.run(tLoginEvent.filter(t => t.loginTime <= endTime && t.loginTime >= startTime).result)
  }


  def getLoginInDataByTimeList(ls:List[(Long,Long)]) = {
    val query = ls.map{v =>getLoginInDataByTime(v._1,v._2).map{r =>(r,v._1)}}
    Future.sequence(query)
  }

  def getObserveDataByRid(recordId:Long) = {
    db.run(tObserveEvent.filter(_.recordid === recordId).result)
  }

  //FIXME 在endTime没有指定的情况下，暂时仅用startTime检测
  def getObserveDataByTime(recordId:Long,startTime:Long,endTime:Long) = {
//    db.run(tObserveEvent.filter(t => t.recordid === recordId && t.outTime >= startTime && t.inTime <= endTime).result)
    db.run(tObserveEvent.filter(t => t.recordid === recordId && t.inTime >= startTime && t.inTime <= endTime).result)
  }

  def getObserveDataByTimeList(recordId:Long,ls:List[(Long,Long)]) = {
    val query = ls.map{v =>getObserveDataByTime(recordId,v._1,v._2).map{r =>(r,v._1)}}
    Future.sequence(query)
  }

  //管理员的方式获取录像信息
  def AdminGetRecordAll(sortBy:String,pageNum:Int,pageSize:Int) = {
    val tNew = sortBy match {
      case "view" =>
        (tRecord join tUserInfo on ((a, b) => a.roomId === b.roomid)).sortBy(_._1.viewNum.desc).drop((pageNum - 1) * pageSize).take(pageSize)
      case "time" =>
        (tRecord join tUserInfo on ((a, b) => a.roomId === b.roomid)).sortBy(_._1.startTime.desc).drop((pageNum - 1) * pageSize).take(pageSize)
      case _ =>
        (tRecord join tUserInfo on ((a, b) => a.roomId === b.roomid)).sortBy(_._1.likeNum.desc).drop((pageNum - 1) * pageSize).take(pageSize)
    }
    val tAll = tNew join tObserveEvent on {case ((a, b), c) => a.id === c.recordid}
    (db.run(tNew.result), db.run(tAll.result))
  }

  //录像id获取观看时长
  def getObserveTimeByRid(recordId: Long) ={
    db.run(tObserveEvent.filter(t => t.recordid === recordId && !(t.outTime === 0l) ).map(t => t.outTime - t.inTime).sum.result)
  }
}
