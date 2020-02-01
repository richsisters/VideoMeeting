###WebClient客户端主要流程

##登录

- WebClient向RoomManager发送登录信息

- 为了实时通信，与RoomManager建立webSocket

##直播

- Web客户端通过UserId和Token向RoomManager获取LiveId和LiveCode

- 将LiveId和LiveCode发送WebRTCSystem

- WebRTCSystem通过LiveId和LiveCode完成RtpServer的鉴权

- WebRTCSystem获取用户的webRTC流，并将其转发到RtpServer

##连麦

- 抢麦时，Web客户端通过UserId，RoomId和Token向RoomManager获取LiveId，
LiveCode，以及房主的 LiveId

- 将自己的LiveId，LiveCode，以及主播的LiveId发送WebRTCSystem

- WebRTC进行抢麦判断，若用户抢麦成功，则为房主和连线者之间，构建WebRTC通讯

- WebRTCSystem获取连线者的webRTC流，并将其转发到RtpServer

##观看直播

- Web客户端从RoomManager处获取到房间列表信息

- 用户点击某一房间列表时，与Processor通信，获取到该房间的MPD文件地址；

- Web客户端从Nignx服务器上拉流（Dash）

- Web客户端可以切换码率观看直播
