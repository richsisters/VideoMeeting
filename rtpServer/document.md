
##### 一、系统主要目标和功能:提供推流，拉流服务

##### 二、关键概念
1、payload：rtp协议的报文中的data中的第一个字节

##### 三、系统主要模块
######拉流客户端：PullStreamClient
1、类声明 PullStreamClient(local_host: String, local_port: Int,
                           pullStreamDst: InetSocketAddress, actor: ActorRef[Protocol.Command])
  *参数说明
  - local_host: 拉流绑定的本地ip
  - local_port: 拉流绑定的本地端口
  - pullStreamDst：发送拉流请求的地址
  - actor：返回消息

2、拉流函数：pullStreamData(liveIds: List[String])
  * 说明
    - 调用拉流函数前
                    1：调用pullStreamStart()，
                    2：actor收到PullStreamReady
    - 参数liveIds为要拉流的liveId列表
    - 重复调用拉流函数后面的liveId列表会覆盖前面的liveId列表
    - 请求拉流成功actor返回PullStreamReqSuccess(liveIds: List[String])，
      liveIds为流的liveId列表（一次请求的liveId太多会分多次返回）
    - 若服务端没有要拉的流，actor返回NoStream(liveIds: List[String])，liveIds为缺少的流的liveId，
    - 请求拉流失败（请求拉流的数据丢包），actor返回PullStreamPacketLoss
    - 流数据actor以PullStreamData(liveId: String, data: Array[Byte])返回，liveId为流Id, data为实际数据

3、关闭函数：close()

4、如果拉的流被关闭，actor返回StreamStop(liveId: String)

######推流客户端：PushStreamClient
1、类声明：PushStreamClient(local_host: String, local_port: Int, pushStreamDst: InetSocketAddress,
                             actor: ActorRef[Protocol.Command])
  *参数说明
  - local_host: 拉流绑定的本地ip
  - local_port: 拉流绑定的本地端口
  - pushStreamDst：推流和鉴权的地址
  - actor：返回消息

2、鉴权函数：auth(liveId: String, liveCode: String)
  * 说明
    - 调用鉴权函数前要保证authStart()函数被调用过
    - 鉴权成功actor返回Protocol.AuthRsp(liveId, true)
    - 鉴权失败actor返回Protocol.AuthRsp(liveId, false)

3、 推流函数：pushStreamData(liveId: String, data: Array[Byte])
  * 说明
    - 调用推流函数前需要要保证authStart()函数被调用过
    - 推流失败actor返回PushStreamError(liveId, errCode, msg)

4、关闭函数：close()

###### ApiService：统计信息接口（ip,端口号，带宽等信息），会在前端显示统计信息

###### StreamService：统计所有流信息等

###### AuthActor:鉴权（通过判断liveCode是否对应当前liveId）

###### DataStoreActor:统计当前拉流，推流的数据信息，会在前端显示

###### PublishActor:收到StreamActor的流，将流转给用户

###### PublishManager:PublishActor的管理类

###### QueryInfoManager:没用到

###### ReceiveActor:payload解析及相应消息处理

###### ReceiveManager:pull和push的管理类

###### StreamActor:进行转流，将流转给PublishActor
 
###### StreamManager:StreamActor的管理类

###### UserManager:获取liveId和liveCode,判断liveId是否存在等

##### 四、系统关键流程
###### 推流
1. 鉴权 
	  * 方式：发送RTP包

	  * 鉴权成功返回头部payloadType字段为102的rtp包
    
    * 鉴权失败返回头部payloadType字段为103的rtp包
        

2. 推流 
    * 鉴权成功后推流
   

###### 拉流 
1. 收到订阅者的GetClientId请求
    * 判断当前ip当前端口号下，是否存在ClientId（存在返回已经存在的，不存在，新生成一个）
    * 发送RTP包
  
2. 收到拉流请求

3. 给订阅者发送包含ssrc的rtp包

4. 收到订阅者返回的收到包含ssrc的rtp包的回复消息

5. 拉流

6. 丢包重传
        
##### 五、系统中容易出错的地方
