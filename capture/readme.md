## 媒体流采集器


### 使用方式 - 非阻塞式消息交互

- 创建类MediaCapture
- set媒体流参数
- 调用start函数
- 调用showPerson函数获取摄像头图像
- 调用showDesktop函数获取桌面图像
- 调用showBoth函数获取摄像头和桌面拼接图像
- 显示桌面图像时应限制不能最大化观看窗口
- 交互消息在protocol文件下Message对象中
- 详细使用方法参考sdk提供的demo或者pcClient中的CaptureActor

