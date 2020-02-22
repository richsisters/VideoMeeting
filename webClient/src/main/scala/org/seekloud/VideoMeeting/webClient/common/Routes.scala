package org.seekloud.VideoMeeting.webClient.common

import org.scalajs.dom

/**
  * create by zhaoyin
  * 2019/7/18  10:20 AM
  */
object Routes {

  private val base = "/VideoMeeting/roomManager"

  object UserRoutes{

    private val urlbase = base + "/user"
    private val urlRecord = base + "/record"

    val userRegister = urlbase + "/signUp"

    val userLogin = urlbase + "/signIn"

    val userLoginByMail = urlbase + "/signInByMail"

    val getRoomList = urlbase + "/getRoomList"

    val searchRoom = urlbase + "/searchRoom"

    val temporaryUser = urlbase + "/temporaryUser"

    def getRecordList(userId:Long, sortBy:String,pageNum:Int,pageSize:Int) = urlRecord + s"/getRecordList?userId=$userId&sortBy=$sortBy&pageNum=$pageNum&pageSize=$pageSize"

    val getOneRecord = urlRecord + "/searchRecord"
    val getAttend = urlRecord + "/getAttend"
    val watchRecordOver = urlRecord + "/watchRecordOver"
    val getCommentInfo = "/VideoMeeting/roomManager/recordComment/getRecordCommentList"
    val sendCommentInfo = "/VideoMeeting/roomManager/recordComment/addRecordComment"

    def uploadImg(imgType:Int, userId:String) = base+s"/file/uploadFile?imgType=$imgType&userId=$userId"

    def nickNameChange(userId:Long,userName:String) = urlbase + s"/nickNameChange?userId=$userId&newName=$userName"
  }

  object AdminRoutes{

    private val urlAdmin = base + "/admin"
    private val urlStat = base + "/statistic"

    val adminSignIn = urlAdmin + "/adminSignIn"

    val admingetUserList = urlAdmin + "/getUserList"

    val adminsealAccount = urlAdmin + "/sealAccount"

    val admincancelSealAccount = urlAdmin + "/cancelSealAccount"

    val adminDeleteRecord = urlAdmin + "/deleteRecord"

    val adminbanOnAnchor = urlAdmin + "/banOnAnchor"

    val loginDataByHour = urlStat + "/loginDataByHour"

    val getLoginData = urlStat + "/getLoginData"

    val watchObserve = urlStat + "/watchObserve"

    val watchObserveByHour = urlStat + "/watchObserveByHour"
  }

  val getToken = base + "/rtmp/getToken"

  def getWsSocketUri(liveId:String,liveCode:String): String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://10.1.29.246:41650/webrtcServer/userJoin?liveId=$liveId&liveCode=$liveCode"
  }

  def rmWebScocketUri(userId:Long, token:String,roomId:Long) = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}/VideoMeeting/roomManager/user/setupWebSocket?userId=$userId&token=$token&roomId=$roomId"
  }
}
