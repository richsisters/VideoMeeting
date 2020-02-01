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
    userId: Long,
    userName: String,
    headImgUrl: String,
    coverImgUrl: String,
    observerNum: Int,
    like: Int,
    streamId: Option[String] = None,
    recordId: Long = 0L,
    timestamp: Long = 0l,
    duration: String = ""
  ) {
    def toRoomInfo =
      RoomInfo(roomId, roomName, roomDes, userId, userName,
        headImgUrl, coverImgUrl, observerNum, like, None, streamId)

    def toRecordInfo =
      RecordInfo(recordId, roomId, roomName, roomDes, userId, userName,
        timestamp, headImgUrl, coverImgUrl, observerNum, like, duration)
  }

  implicit class RichRoomInfo(r: RoomInfo) {
    def toAlbum: AlbumInfo =
      AlbumInfo(
        r.roomId,
        r.roomName,
        r.roomDes,
        r.userId,
        r.userName,
        r.headImgUrl,
        r.coverImgUrl,
        r.observerNum,
        r.like,
        streamId = r.rtmp
      )
  }

  implicit class RichRecordInfo(r: RecordInfo) {
    def toAlbum: AlbumInfo =
      AlbumInfo(
        r.roomId,
        r.recordName,
        r.recordDes,
        r.userId,
        r.userName,
        r.headImg,
        r.coverImg,
        r.observeNum,
        r.likeNum,
        recordId = r.recordId,
        timestamp = r.startTime,
        duration = r.duration
      )
  }
}
