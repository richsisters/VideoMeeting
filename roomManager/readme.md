### Room Manager主要API：


## 用户登录 

- url：
- 方法：

- 请求：

```
{
	userName:String,
	userPassword:String
}
```

- 返回：待定
```
{
	userId:Long,
	nickname:String,
	token:String
}
```

## 建立webSocket连接

- url:
- 方法：


- 请求
```
{
	userId:Long,
	token:String
}
```

## 申请直播（主播调用，通过webSocket）
- url：
- 方法：


- 请求：
```
{
	userId4Boss:Long,
	token:String
}
```
- 返回：
```
{
	liveId:String,
	liveCode:String
}
```
## 校验直播推流是否授权（流服务器调用，暂时不做鉴权）
- url：
- 方法：


- 请求：
```
{
	liveId:String,
	liveCode:String	
}
```
- 返回：
```
{
	verified:Boolean
}
```

## 客户端申请连线（观众调用，通过webSocket）
- url：
- 方法：


- 请求：
```
{
	userId4Chat:Long,
	roomId:Long
}
```

## 房主拒绝连线（通过webSocket）
- url：
- 方法：

- 请求：

```
{
	userId4Chat:Long,
	roomId:Long
}
```

## 房主同意连线（通过webSocket）
- url：
- 方法：

- 请求：

```
{
	userId4Chat:Long,//
	roomId:Long
}
```
- 返回：

```
{
	liveId4Chat:String,
	liveCode:String,
	liveId4Owner:String
}
```

## 房主断开连线（通过webSocket）
- url：
- 方法：

- 请求：
```
{
	roomId:Long
}
```
## 房间信息

- url：
- 方法：

- 请求：
```
case class RoomInfo{
	roomId:Long,
	...
}
```
## 获取房间列表

- 返回：

```
{
	data:List[RoomInfo]
}
```

## 观众查询房间
- url：
- 方法：


- 请求：

```
{
	roomId:Long
}
```

- 返回：
```
{
	roomInfo:RoomInfo,
	mdpAddress:String
}
```

## RtmpToken换取LiveId和LiveCode（RTMP System调用）

- url：
- 方法：

- 请求：

```
{
	rtmpToken:String
}
```

- 返回

```
{
	liveId:String,
	liveCode:String
}
```

