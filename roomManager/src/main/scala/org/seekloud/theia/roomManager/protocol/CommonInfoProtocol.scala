package org.seekloud.VideoMeeting.roomManager.protocol

import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.RoomInfo

object CommonInfoProtocol {

  //fixme isJoinOpen,liveInfoMap字段移到这里
  final case class WholeRoomInfo(
                                var roomInfo:RoomInfo,
                                var layout:Int = CommonInfo.ScreenLayout.EQUAL,
                                var aiMode:Int = 0
                                )

}
