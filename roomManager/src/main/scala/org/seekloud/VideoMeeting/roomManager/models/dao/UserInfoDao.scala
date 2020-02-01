package org.seekloud.VideoMeeting.roomManager.models.dao

import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{RoomInfo, UserDes}
import org.seekloud.VideoMeeting.roomManager.common.{AppSettings, Common}
import org.seekloud.VideoMeeting.roomManager.models.SlickTables._
import org.seekloud.VideoMeeting.roomManager.utils.DBUtil.db
import slick.jdbc.PostgresProfile.api._
import org.seekloud.VideoMeeting.roomManager.Boot.executor
import org.seekloud.VideoMeeting.roomManager.utils.SecureUtil

import scala.concurrent.Future


object UserInfoDao {

  def getHeadImg(headImg:String):String = {
    if(headImg == "")Common.DefaultImg.headImg else headImg
  }

  def getCoverImg(coverImg:String):String = {
    if(coverImg == "")Common.DefaultImg.coverImg else coverImg
  }

  def getVideoImg(coverImg:String):String = {
    if(coverImg == Common.DefaultImg.coverImg)Common.DefaultImg.videoImg else coverImg
  }

  def addUser(email:String, name:String, pw:String, token:String, timeStamp:Long,rtmpToken:String) = {
    db.run(tUserInfo += rUserInfo(1, name, pw, 1, token, timeStamp,Common.DefaultImg.headImg,Common.DefaultImg.coverImg,email,timeStamp,rtmpToken))
  }

  def modifyImg4User(userId:Long,fileName:String,imgType:Int) = {
    if(imgType == CommonInfo.ImgType.coverImg){
      db.run(tUserInfo.filter(_.uid === userId).map(_.coverImg).update(fileName))
    }else{
      db.run(tUserInfo.filter(_.uid === userId).map(_.headImg).update(fileName))
    }

  }

  def checkEmail(email:String) ={
    db.run(tUserInfo.filter(_.email === email).result.headOption)
  }


  def searchByName(name:String) = {
    db.run(tUserInfo.filter(i => i.userName === name).result.headOption)
  }

  def searchById(uid:Long) = {
    db.run(tUserInfo.filter(i => i.uid === uid).result.headOption)
  }

  def searchByRoomId(roomId:Long) = {
    db.run(tUserInfo.filter(i => i.roomid === roomId).result.headOption)
  }

  def getUserDes(users: List[Long]) = {
    Future.sequence(users.map{uid =>
      db.run(tUserInfo.filter(t => t.uid === uid).result)}).map(_.flatten).map{user =>
        user.map(r => UserDes(r.uid, r.userName,if(r.headImg == "") Common.DefaultImg.headImg else r.headImg)).toList
    }
  }

  def verifyUserWithToken(uid:Long,token:String):Future[Boolean] = {
    for{
      value <- db.run(tUserInfo.filter(i => i.uid === uid).map(t => (t.token,t.tokenCreateTime)).result.headOption)
    }yield {
      if(value.nonEmpty){
        if(value.get._1 == token && System.currentTimeMillis() - value.get._2 <= AppSettings.tokenExistTime * 1000L){
          true
        }else{
          false
        }
      }else{
        false
      }
    }
  }

  def updateToken(uid:Long,token:String,timeStamp:Long) = {
    db.run(tUserInfo.filter(i => i.uid === uid).map(l=>
      (l.token,l.tokenCreateTime)
    ).update(token,timeStamp))
  }

  def updateHeadImg(uid:Long,headImg:String) = {
    db.run(tUserInfo.filter(i => i.uid === uid).map(l=> (l.headImg)
    ).update(headImg))
  }

  def updateName(uid:Long,name:String) = {
    db.run(tUserInfo.filter(i => i.uid === uid).map(l=> (l.userName)
    ).update(name))
  }

  def updatePsw(uid:Long,psw:String) = {
    db.run(tUserInfo.filter(i => i.uid === uid).map(l=> (l.password)
    ).update(psw))
  }

  def updateCoverImg(uid:Long,coverImg:String) = {
    db.run(tUserInfo.filter(i => i.uid === uid).map(l=> (l.coverImg)
    ).update(coverImg))
  }

  def getRtmpToken(userId:Long) = {
    db.run(tUserInfo.filter(_.uid === userId).map(_.rtmpToken).result)
  }

  def checkRtmpToken(userId:Long,rtmpToken:String) = {
    db.run(tUserInfo.filter(t => t.uid === userId && t.rtmpToken === rtmpToken).result.headOption)
  }

  def updateRtmpToken(userId:Long) = {
    for{
      t <- db.run(tUserInfo.filter(_.uid === userId).map(_.rtmpToken).update(SecureUtil.nonceStr(40)))
      r <- searchById(userId)
    }yield {
      r
    }

  }

  def getUserLen = {
    db.run(tUserInfo.length.result)
  }

  def getRoomByRoomId(roomId:Long) = {
    db.run(tUserInfo.filter(_.roomid === roomId).result)
  }

  def deleteUserByEmail(email:String,password:String) = {
    db.run(tUserInfo.filter(r => r.email === email ).delete)
  }

  def main(args: Array[String]): Unit = {
    val a = searchById(100001)
    Thread.sleep(3000)
    println(a)
  }
}
