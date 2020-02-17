## 媒体流采集器


### 使用方式 - 非阻塞式消息交互

- 创建类MediaCapture
- set媒体流参数
- 调用start函数
- 调用showPerson函数获取摄像头图像
- 调用showDesktop函数获取桌面图像
- 调用showBoth函数获取摄像头和桌面拼接图像（桌面为背景，默认摄像头图像在左上角）
- 调用changeCameraPosition(position: Int)函数改变摄像头图像在拼接图像中的位置（0：左上  1：右上  2：右下  3：左下）
- 显示桌面图像时应限制不能最大化观看窗口
- 交互消息在protocol文件下Message对象中
- 开始录像时调用stop(startOutputFile: Boolean = false)函数需要传入参数true
- 详细使用方法参考sdk提供的demo或者pcClient中的CaptureActor

