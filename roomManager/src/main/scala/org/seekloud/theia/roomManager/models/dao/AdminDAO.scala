package org.seekloud.VideoMeeting.roomManager.models.dao
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.UserInfo
import org.seekloud.VideoMeeting.roomManager.models.SlickTables
import slick.jdbc.PostgresProfile.api._
import org.seekloud.VideoMeeting.roomManager.Boot.executor
import org.seekloud.VideoMeeting.roomManager.utils.DBUtil._

import scala.concurrent.Future
import org.seekloud.VideoMeeting.roomManager.models.SlickTables._
/**
  * created by benyafang on 2019/9/23 16:58
  * */
object AdminDAO {
  def sealUserInfo(userId:Long,sealUtilTime:Long) = {
    db.run(tUserInfo.filter(_.uid === userId).map(r => (r.`sealed`,r.sealedUtilTime)).update((true,sealUtilTime)))
  }

  def cancelSealUserInfo(userId:Long) = {
    db.run(tUserInfo.filter(_.uid === userId).map(r => (r.`sealed`,r.sealedUtilTime)).update((false,-1)))
  }

  def getUserList(pageNum:Int,pageSize:Int) ={
    for{
      len <- UserInfoDao.getUserLen
      ls <- db.run(tUserInfo.sortBy(_.uid).take(pageNum * pageSize).drop((pageNum - 1) * pageSize).result)
    }yield {
      (len,ls)
    }

  }

  def updateAllowAnchor(userId:Long,allow:Boolean) = {
    db.run(tUserInfo.filter(_.uid === userId).map(_.allowAnchor).update(allow))
  }

}
