package org.seekloud.VideoMeeting.pcClient

import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{RecordInfo, RoomInfo}

/**
  * User: TangYaruo
  * Date: 2019/9/16
  * Time: 20:08
  */
package object common {
  /*room or record*/
  case class AlbumInfo(
    roomId: Long,
    roomName: String,
    roomDes: String,
    coverImgUrl: String,
    userId: Long,
    userName: String,
    headImgUrl: String,
    streamId: Option[String] = None,
    recordId: Long = 0L,
    timestamp: Long = 0l,
    duration: String = ""
  ) {
    def toRoomInfo =
      RoomInfo(roomId, roomName, roomDes, coverImgUrl, userId, userName,
        headImgUrl, None, streamId)

    def toRecordInfo =
      RecordInfo(recordId, roomId, roomName, roomDes, coverImgUrl, userId, userName,
        timestamp, headImgUrl, duration)
  }

  implicit class RichRoomInfo(r: RoomInfo) {
    def toAlbum: AlbumInfo =
      AlbumInfo(
        r.roomId,
        r.roomName,
        r.roomDes,
        r.coverImgUrl,
        r.userId,
        r.userName,
        r.headImgUrl,
        streamId = r.rtmp
      )
  }

  implicit class RichRecordInfo(r: RecordInfo) {
    def toAlbum: AlbumInfo =
      AlbumInfo(
        r.roomId,
        r.recordName,
        r.recordDes,
        r.coverImg,
        r.userId,
        r.userName,
        r.headImg,
        recordId = r.recordId,
        timestamp = r.startTime,
        duration = r.duration
      )
  }
}
