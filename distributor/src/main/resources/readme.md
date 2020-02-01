### 更新房间信息
```
req:
case class UpdateRoomInfo(
  roomId:Long,
  liveIdList:List[String], //主播放在第一个
  layout:Int,
  aiMode:Int //为了之后拓展多种模式，目前0为不开ai，1为人脸目标检测 
)
rsp:
{
 "errCode": 0,
 "msg": "OK"
}
```
### 关闭房间
```
req:
case class CloseRoom(
  roomId:Long
)
rsp:
{
 "errCode": 0,
 "msg": "OK"
}
```
### 查询mpd地址
```
req:
case class GetMpd(
  roomId:Long
)
rsp:
{
  "mpd":"string",
  "errCode": 0,
  "msg": "OK"
}
```