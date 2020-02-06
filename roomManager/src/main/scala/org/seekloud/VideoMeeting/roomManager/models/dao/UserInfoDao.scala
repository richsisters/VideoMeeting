package org.seekloud.VideoMeeting.roomManager.models.dao

import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{RoomInfo, UserDes}
import org.seekloud.VideoMeeting.roomManager.common.{AppSettings, Common}
import org.seekloud.VideoMeeting.roomManager.models.SlickTables._
import org.seekloud.VideoMeeting.roomManager.utils.DBUtil.db
import slick.jdbc.PostgresProfile.api._
import org.seekloud.VideoMeeting.roomManager.Boot.executor
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

  def addUser(email:String, name:String, pw:String, token:String, timeStamp:Long) = {
    db.run(tUserInfo += rUserInfo(0L, name, pw, 1, token, timeStamp, Common.DefaultImg.headImg, email))
  }

  def modifyImg4User(userId:Long,fileName:String,imgType:Int) = {
    db.run(tUserInfo.filter(_.uid === userId).map(_.headImg).update(fileName))
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
        user.map(r => UserDes(r.uid, r.userName,if(r.headImg == "") Common.DefaultImg.headImg else r.headImg))
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

  def getUserLen = {
    db.run(tUserInfo.length.result)
  }

  def getRoomByRoomId(roomId:Long) = {
    db.run(tUserInfo.filter(_.roomid === roomId).result)
  }

  def deleteUserByEmail(email:String,password:String) = {
    db.run(tUserInfo.filter(r => r.email === email ).delete)
  }

  def test() = {
    val q = tLoginEvent += rLoginEvent(0L, 1L, 2L)
    db.run(q)
  }

  def main(args: Array[String]): Unit = {
    println(AppSettings.slickUrl)
    test().map{r =>
      println("res" + r)
    }.recover{
      case exception: Exception =>
        println(s"$exception")
    }
    println("finish")
  }
}
