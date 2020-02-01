### rtp-server


#### 接口 host：10.1.29.248

## RTP版本

###### RtpClient
#### 测试环境：10.1.29.246 
#### 生产环境：10.1.29.244
#### http端口：30390
#### 推流udp端口: 61040
#### 拉流udp端口: 61041


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


###### 推流
1. 鉴权 port：8081
	* 方式：发送RTP包
	* 发送rtp包说明
		- 头部第一个字节0x80
		- 头部payloadType字段：101
		- payload：liveId和liveCode，以井号分隔（例：100001#Code）(UTF-8编码）

	* 鉴权成功返回rtp包说明
	    - 头部payloadType字段：102
	    - 可直接解析头部SSRC字段获得SSRC
	    - payload：对应的liveId （例：100001）(UTF-8编码）

	* 鉴权失败返回rtp包说明
        - 头部payloadType字段：103
        - payload：对应的liveId （例：100001）(UTF-8编码）

2. 推流 port：8081
    * 方式：发送RTP包
    * 说明：
        - 头部第一个字节0x80
        - 头部SSRC字段为鉴权时server返回的SSRC
        - 头部payloadType字段：33
        - 头部其他字段按标准RTP协议填写
        - payload：多个ts packet

###### 拉流
1. processor请求拉流 port：8082
	* 方式：发送RTP包
	* 发送rtp包说明
		- 头部第一个字节0x80
		- 头部payloadType字段：111
		- payload：多个liveId，以井号分隔（例：100001#100002#100003）(UTF-8编码）

	* 返回rtp包说明
	    - 头部payloadType字段：112

	    - payload：多个（liveId，SSRC）键值对（例：100001#SSRC1;100002#SSRC2)(UTF-8编码）

2. 拉流 port：8082
    * 方式：接收RTP包
    * 说明：
        - 头部第一个字节0x80
        - 不同的SSRC字段对应不同的liveId
        - 头部payloadType字段：33
        - payload：多个ts packet

## udp版本   
* 自定义头部字节：
    - 第一个字段：payloadType，占一个字节
    - 第二个字段：自增序列号，Int, 占四个字节
    - 第三个字段：ssrc，Int，占四个字节
            
###### 推流 
1. 鉴权 port：30681
	* 方式：发送UDP包
	* 发送UDP包说明
		- payloadType字段：101
		- payload：liveId和liveCode，以井号分隔（例：100001#Code）(UTF-8编码）
		
	* 鉴权成功返回UDP包说明
	    - 头部payloadType字段：102
	    - 可直接解析头部SSRC字段获得SSRC
	    - payload：对应的liveId （例：100001）(UTF-8编码）
	    
	* 鉴权失败返回UDP包说明
        - 头部payloadType字段：103
        - payload：对应的liveId （例：100001）(UTF-8编码）
	
2. 推流 port：30681
    * 方式：发送UDP包
    * 说明：
        - 头部SSRC字段为鉴权时server返回的SSRC
        - 头部payloadType字段：33
        - payload：多个ts packet
        
###### 拉流
1. processor请求拉流 port：30682
	* 方式：发送UDP包
	* 发送UDP包说明
		- 头部payloadType字段：111
		- payload：多个liveId，以井号分隔（例：100001#100002#100003）(UTF-8编码）
		
	* 返回rtp包说明
	    - 头部payloadType字段：112
	    - payload：多个（liveId，SSRC）键值对（例：100001#SSRC1;100002#SSRC2)(UTF-8编码）
	
2. 拉流 port：30682
    * 方式：接收UDP包
    * 说明：
        - 不同的SSRC字段对应不同的liveId
        - 头部payloadType字段：33
        - payload：多个ts packet 



        


