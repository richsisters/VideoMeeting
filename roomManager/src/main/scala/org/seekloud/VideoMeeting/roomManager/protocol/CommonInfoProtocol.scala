package org.seekloud.VideoMeeting.roomManager.protocol

import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{LiveInfo, RoomInfo}

object CommonInfoProtocol {

  final case class WholeRoomInfo(
                                var roomInfo:RoomInfo,
                                var liveInfo: LiveInfo, //房间主持人的liveInfo
                                var isJoinOpen:Boolean = false,
                                var layout:Int = CommonInfo.ScreenLayout.EQUAL,
                                var aiMode:Int = 0
                                )

}
