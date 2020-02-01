### 开始拉流
```
POST请求
URL：/theia/distributor/startPull
req:
case class StartPullReq(
    roomId:Long,
    liveId:String
  )
rsp:
case class StartPullRsp(
    errCode:Int,
    msg:String,
    liveAdd:String,
    startTime:Long
  )
```
### 结束拉流
```
POST请求
URL：/theia/distributor/finishPull
req:
case class FinishPullReq(
  liveId:String
)
rsp:
{
  "errCode": 0,
  "msg": "OK"
}
```
### 查询某条流状态
```
POST请求
URL：/theia/distributor/checkStream
req:
case class CheckStreamReq(
  liveId:String
)
rsp:
case class CheckStreamRsp(
    errCode:Int,
    msg:String,
    status:String
  )
```
### 查找录像
```
POST请求
URL：/theia/distributor/seekRecord
req:
case class SeekRecord(
    roomId:Long,
    startTime:Long
    )
rsp:
case class RecordInfoRsp(
    errCode:Int = 0,
    msg:String = "ok",
    duration:String
  )
```