## tDistributor()

### 一、功能描述

* 更新房间和关闭房间相关处理
* 输入流处理和输出流
* 对接processor平台


### 二、系统流程


### 三、功能模块

#### distributor
- resource
  - application.conf
    - 配置文件

- scala.seekloud.VideoMeeting.distirbutor
  - common
    - AppSetting
        - 管理application配置文件（可对akka进行配置管理）
    - Constants
        - 当前仅定义了一个String属性
  - core
    - DistributorWorker
        - 更新房间,关闭房间信息传给encodeManager
        - work
            - 收到roomid和port就将其存在map中，收到RevActor来的buf就将其拆分并解析，如果roomUdpMap中存在roomid就把data.boby传过去，
            收到newLive时传给encodeManager房间名及开始时间，收到关闭信号时传给encodeManager房间名
    - EncodeActor
        - work
            -收到EncodeManager传来的port, startTime，再将newStartTime, 本身的roomId传给saveManager
    - EncodeManager
        - 编码Actor管理，生成多个不同roomid的EncodeActor
        - create
        - work
            - 收到Distributor传来的roomId, startTime开启一个新端口，再给Distributor传回roomId, port，
            如果roomid已经有了，就将port, startTime传给roomid对应的EncodeActorRef
    - RevActor
        - removeTestFile
            - 删除指定文件
        - create
            - 创建actor,内部建缓冲区;创建tcp socket，并获取inputStream，运行revThread和testThread线程，执行work
        - work
            - 监视actor是否收到Stop,收到后停止Actor以及子Actor
        - testThread
            - 创建线程用于调用ffmpeg，用于isTest测试
        - revThread
            - 创建线程用于将从socket得到的buf数据传给distributor，出错则reStart
        - reStart
            - socket未能成功开启时调用，关闭所有房间并重新监听(获取distributor获取的roomid和端口地址,
            移除roomid并将端口地址传给encodeManager,encodeManager调用remove，移除roomid并传给encodeActor Stop类，
            encodeActor收到Stop就关闭本机进程（相对的方法是ProcessBuilder.start() ），然后给saveManager发送NewSave，...)
    - SaveActor
        - create
            - 被SaveManager开启后，就创建一个CreateFFmpeg对象，并在CreateFFmpeg中连锁运行一系列ffmpeg函数，
            将fileLocation中的m4s转为mp4，其中要删除之前的无用record.mp4
            - work
                - 在收到Stop指令后，删除无用的video.mp4和audio.mp4，并关闭Actor
    - SaveManager
        - work
            - 收到EncodeActor传来的newStartTime, roomId，用以生成一个新的SaveActor
            
    distributor -> encodeManager
    revActor -> encodeManager
    
    revActor->distributor
    encodeManager->distributor
    
    FileServer->SaveManager（自带saveActor.create()）
    encodeActor->SaveManager
     
    
    
    
  - http
    - FileService
        - 包含fileRoutes路径，连带包含getFile ~ getRecord ~ seekRecord ~ removeRecords,
        这些接口在和distributor同一级的模块的Routes文件夹中定义
        - getFile 
            - 读取/mix下的指定文件
        - getRecord 
            - 读取/record下的指定文件
        - seekRecord
            - 传消息给saveManager，向saveManager请求SeekRecord（是否有record file）
        - removeRecords
            - 传消息给saveManager，向saveManager发送RemoveRecords消息
    - HttpService
        - 类中的routes被Boot继承并绑定，连带绑定resourceRoutes ~ fileRoute
    - ResourceService
        - 执行resourceRoutes，访问一些资源路径,通常在前端的Route文件中定义
    - SessionBase
        - 定义了一个videoAuth，在同一层的Service中返回当前Session
    
  - protocol
    - CommonErrorCode
        - 存放解析错误函数
    - SharedProtocol
        - 存放样例类,用来传输协议
  - utils
    - 多功能、基于工具的包
  - Boot
    - 绑定routes，interface，port，传入Http()并执行akka框架对其进行处理。
     （其中routes是从HttpService继承，接着匹配 两个连接的Route类型。其中
     的fileRoute是从FileService继承。）
     运行各个actor
    
        
