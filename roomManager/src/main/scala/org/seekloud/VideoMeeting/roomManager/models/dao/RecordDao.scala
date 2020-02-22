package org.seekloud.VideoMeeting.roomManager.models.dao

import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{RecordInfo, UserInfo}
import org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager.Processor.RecordData
import org.seekloud.VideoMeeting.roomManager.utils.DBUtil._
import org.seekloud.VideoMeeting.roomManager.models.SlickTables._
import slick.jdbc.PostgresProfile.api._
import org.seekloud.VideoMeeting.roomManager.Boot.executor

import scala.collection.mutable
import scala.concurrent.Future

object RecordDao {

  def addRecord(roomId:Long, recordName:String, recordDes:String, startTime:Long, coverImg:String, viewNum:Int, likeNum:Int,duration:String,host: Long, attendList: List[Long]) = {
    val attend = attendList.mkString(";")
    val hostAndAttend = host.toString + ";" + attend
    db.run(tRecord += rRecord(1, roomId, startTime, coverImg, recordName, recordDes, viewNum, likeNum, duration, "", hostAndAttend))
  }

  def searchRecord(roomId:Long, startTime:Long):Future[Option[RecordInfo]] = {
    val record = db.run(tRecord.filter(i => i.roomId === roomId && i.startTime === startTime).result.headOption)
    record.flatMap{resOpt =>
      if (resOpt.isEmpty)
        Future(None)
      else{
        val r = resOpt.get
        val res = UserInfoDao.searchByRoomId(r.roomId).map{w =>
          if(w.nonEmpty){
            Some(RecordInfo(r.id,r.roomId,r.recordName,r.recordDes,UserInfoDao.getCoverImg(r.coverImg),
              w.get.uid,w.get.userName,r.startTime, UserInfoDao.getHeadImg(w.get.headImg),r.duration))
          }else{
            log.debug("获取主播信息失败，主播不存在")
            Some(RecordInfo(r.id,r.roomId,r.recordName,r.recordDes,UserInfoDao.getCoverImg(r.coverImg), -1l,"",r.startTime,
              UserInfoDao.getHeadImg(""),r.duration))
          }
        }
        res
      }
    }
  }

  def deleteRecord(recordId:Long) = {
    db.run(tRecord.filter(_.id === recordId).delete)
  }

  def getAttend(recordId:Long) ={
    db.run(tRecord.filter(_.id === recordId).result)
  }

  def searchRecordById(recordId:Long) ={
    db.run(tRecord.filter(_.id === recordId).result.headOption)
  }


  def searchRecordById(recordIdList:List[Long]) ={
    Future.sequence(recordIdList.map{id =>
      db.run(tRecord.filter(_.id === id).result.headOption)
    }).map{r => r.filter(_.nonEmpty).map(_.get).map(r => RecordData(r.roomId,r.startTime))}
  }

  def deleteRecordById(recordIdList:List[Long]) ={
    val query = tRecord.filter{r =>
      recordIdList.map{r.id === _}.reduceLeft(_ || _)
    }
    db.run(query.delete)
  }



  def getRecordAll(userId: String, sortBy:String,pageNum:Int,pageSize:Int) :Future[List[RecordInfo]]= {
    val records = if (sortBy == "time") db.run(tRecord.filter(_.attend.like(s"%$userId%")).sortBy(_.startTime.reverse).drop((pageNum - 1) * pageSize).take(pageSize).result)
    else if (sortBy == "view") db.run(tRecord.sortBy(_.viewNum.reverse).drop((pageNum - 1) * pageSize).take(pageSize).result)
    else db.run(tRecord.sortBy(_.likeNum.reverse).drop((pageNum - 1) * pageSize).take(pageSize).result)
    records.flatMap{ls =>
      val res = ls.map{r =>
        UserInfoDao.searchByRoomId(r.roomId).map{w =>
          if(w.nonEmpty){
            RecordInfo(r.id,r.roomId,r.recordName,r.recordDes,UserInfoDao.getCoverImg(r.coverImg),w.get.uid,w.get.userName,r.startTime,
              UserInfoDao.getHeadImg(w.get.headImg),r.duration)
          }else{
            log.debug("获取主播信息失败，主播不存在")
            RecordInfo(r.id,r.roomId,r.recordName,r.recordDes,UserInfoDao.getCoverImg(r.coverImg),-1l,"",r.startTime,
              UserInfoDao.getHeadImg(""),r.duration)
          }
        }
      }.toList
      Future.sequence(res)
    }
  }


  def getTotalNum(userId: String) = {
    db.run(tRecord.filter(_.attend.like(s"%$userId%")).length.result)
  }

  def updateViewNum(roomId:Long, startTime:Long, num:Int) = {
    db.run(tRecord.filter(i => i.roomId === roomId && i.startTime === startTime).map(_.viewNum).update(num))

  }

  def getAuthorRecordList(roomId: Long): Future[List[RecordInfo]] = {
    val resList = UserInfoDao.searchByRoomId(roomId).flatMap{
      case Some(author) =>
        val records = db.run(tRecord.filter(_.roomId === roomId).sortBy(_.startTime.reverse).result)
        records.map{ls =>
          val res = ls.map{r =>
            RecordInfo(r.id,r.roomId,r.recordName,r.recordDes,UserInfoDao.getCoverImg(r.coverImg),author.uid,author.userName,r.startTime,
              UserInfoDao.getHeadImg(author.headImg),r.duration)
          }.toList
          res
        }
      case None =>
        log.debug("获取主播信息失败，主播不存在")
        Future{List.empty[RecordInfo]}
    }

    resList
  }

  def getAuthorRecordTotalNum(roomId: Long): Future[Int] = {
    db.run(tRecord.filter(_.roomId === roomId).length.result)
  }

  def deleteAuthorRecord(recordId: Long) = {
    db.run(tRecord.filter(_.id === recordId).delete)
  }

  def addRecordAddr(recordId: Long, recordAddr: String): Future[Int] = {
    db.run(tRecord.filter(_.id === recordId).map(_.recordAddr).update(recordAddr))
  }


  def main(args: Array[String]): Unit = {

    val a = List(100,200,300)
    println(a)
//    getTotalNum("10069").map{
//      r =>
//        println("okokokokokoko" + r)
//    }.recover{
//      case x : Exception =>
//        println(s"${x.getMessage}")
//    }
  }
}
