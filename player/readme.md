## 视频播放器


### 使用方式 - 非阻塞式消息交互

- 创建类MediaPlayer

- 调用start函数,开始播放:

  若需要自主播放则传入canvas的GraphicsContext,否则只输出数据不播放
  
  若需要更改媒体流参数则传入mediaSettings, 否则为默认值
  
  播放流的类型为 Either[String, InputStream]
  
  
- 交互消息在protocol文件下Messages对象中


- 详细使用方法参考sdk提供的demo或者pcClient中的VideoPlayer


### 播放器使用示例 - demo/Test1

- 运行该示例，页面上有左右两个播放窗口，每个播放窗口下对应 4个button：start，pause，continue，stop


- startBtn：调用 mediaActor中 start方法，开始播放视频

  pauseBtn：调用 mediaActor中 pause方法，暂停绘制画面和播放声音，grabber依然工作
  
  continueBtn：调用 mediaActor中 pause方法，恢复绘制画面和播放声音
  
  stopBtn：调用 mediaActor中 stop方法，停止播放（停止绘制画面、播放声音、抓取帧），stop Actors
 
 