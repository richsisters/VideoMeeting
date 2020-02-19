package org.seekloud.VideoMeeting.protocol.ptcl

/**
  * User: TangYaruo
  * Date: 2019/7/15
  * Time: 22:05
  */
object CommonInfo {

  object ScreenLayout {
    val EQUAL = 0 //对等窗口
    val HOST_MAIN_LEFT = 1 //大小窗口（主播大，观众再左边）
    val HOST_MAIN_RIGHT = 1 //大小窗口（主播大，观众再右边）
    val AUDIENCE_MAIN_LEFT = 2 //大小窗口（观众大，主播在左边）
    val AUDIENCE_MAIN_RIGHT = 2 //大小窗口（观众大，主播在右边）
  }

  object ClientType {
    val PC = 0
    val WEB = 1

  }

  object AiMode{
    val close = 0
    val face = 1
  }



  object CameraPosition{
    val left_top = 0
    val right_top = 1
    val right_bottom = 2
    val left_bottom = 3
  }

  object ImgType{
    val headImg = 0//头像图片
    val coverImg = 1//封面图片
  }

  case class UserInfo(
    userId: Long,
    userName: String,
    headImgUrl:String,
    token: String,
    tokenExistTime: Long, //token有效时长
    seal:Boolean = false
  )

  case class UserDes(
    userId: Long,
    userName: String,
    headImgUrl:String
                    )

  case class RoomInfo(
    //房间基本设置
    roomId: Long,
    roomName: String,
    roomDes: String,
    coverImgUrl:String,
    //房主（主持人）设置
    var userId: Long,
    var userName:String,
    var headImgUrl:String,
    //用于客户端显示的房间流信息
    var rtmp: Option[String] = None
  )

  case class RecordInfo(
                         recordId:Long,//数据库中的录像id，用于删除录像
                         roomId:Long,
                         recordName:String,
                         recordDes:String,
                         coverImg:String,
                         userId:Long,
                         userName:String,
                         startTime:Long,
                         headImg:String,
                         duration:String = ""
                       )

  /**
    * webrtcServer中已调用
    * */
  case class LiveInfo(
    liveId: String,
    liveCode: String
  )

  /*参会者信息*/
  case class AudienceInfo(
    userId: Long,
    userName: String,
    userHeadImg: String,
    liveId: String
  )

}
