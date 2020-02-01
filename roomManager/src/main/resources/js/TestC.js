//dashjs播放
function DashType(url) {
    this.url = url;
    this.player = dashjs.MediaPlayer().create();
    this.controlbar = new ControlBar(this.player);
}
DashType.prototype.constructor = DashType;
var lastBufferLevel;
var s;
DashType.prototype.initialize = function (that) {
    var dashMer;
    var t = setTimeout(function (){
        if(document.getElementsByClassName("dash-js").length != 0){
            that.player.initialize(document.querySelector("#videoplayer"), that.url, true);
            that.player.updateSettings({
                'streaming': {
                    // 'liveDelay': 12, //目标延迟（单位s）
                    'lowLatencyEnabled': true, //允许liveCatchUpMinDrift和liveCatchUpPlaybackRate发生作用
                    'liveCatchUpMinDrift': 30, //追赶机制目标延迟和实际延迟的最小差 0-0.5（单位s）
                    // 'liveCatchUpMaxDrift': 30, //跳帧机制目标延迟和实际延迟的最大差（单位s）
                    'liveCatchUpPlaybackRate': 0.5 //当目标延迟和实际延迟的差大于liveCatchUpMinDrift时的最大追赶百分比 0%-50%
                }
            });
            that.controlbar.initialize();
            dashMer = that.player.getDashMetrics();
            clearInterval(t);
        }
    },500);

    s = setInterval(function () {
        // console.log("几倍速：   "+that.player.getPlaybackRate());
        // console.log("延时：   "+that.player.getCurrentLiveLatency());
        // console.log(dashMer.getCurrentBufferLevel("video"));
        var loadTag = document.getElementById("load2");
        // 暂时先做简单的判断，即当bufferLevel两次不变说明直播流卡住了
        if(loadTag !== null){
            if(dashMer.getCurrentBufferLevel("video") === 0 || lastBufferLevel === dashMer.getCurrentBufferLevel("video")){
                loadTag.setAttribute("style","display:block");
            }else{
                loadTag.setAttribute("style","display:none");
                lastBufferLevel = dashMer.getCurrentBufferLevel("video");
            }
        }else{
            clearInterval(s)
        }
    },500)
};
DashType.prototype.reset = function (that) {
    that.player.reset();
    that.controlbar.reset();
};

//hls播放 videojs
var player = null;
function HlsType(url) {
    this.url = url;
}
HlsType.prototype.constructor = HlsType;
HlsType.prototype.initialize = function (that) {
    var t = setInterval(function () {
        if(document.getElementsByClassName("video-js").length != 0){
            player = videojs('hls-video', { "poster": "", "controls": "true" }, function() {
                this.on('play', function() {
                    clearInterval(s)
                    console.log('正在播放');
                });
                //暂停--播放完毕后也会暂停
                this.on('pause', function() {
                    console.log("暂停中")
                });
                // 结束
                this.on('ended', function() {
                    console.log('结束');
                })
            });
            clearInterval(t);
        }
    },500);
};

HlsType.prototype.dispose = function(that){
    player.dispose();
};

//拍照并转换为文件上传功能
var mysteam;
function TakePhotoFile() {
}
TakePhotoFile.prototype.constructor = TakePhotoFile;
TakePhotoFile.prototype.takephoto = function (that) {
    // 旧版本浏览器可能根本不支持mediaDevices，我们首先设置一个空对象
    if (navigator.mediaDevices === undefined) {
        navigator.mediaDevices = {};
    }
    // 一些浏览器实现了部分mediaDevices，我们不能只分配一个对象
    // 使用getUserMedia，因为它会覆盖现有的属性。
    // 这里，如果缺少getUserMedia属性，就添加它。
    if (navigator.mediaDevices.getUserMedia === undefined) {
        navigator.mediaDevices.getUserMedia = function (constraints) {
            // 首先获取现存的getUserMedia(如果存在)
            var getUserMedia = navigator.webkitGetUserMedia || navigator.mozGetUserMedia;
            // 有些浏览器不支持，会返回错误信息
            // 保持接口一致
            if (!getUserMedia) {
                return Promise.reject(new Error('getUserMedia is not implemented in this browser'));
            }
            //否则，使用Promise将调用包装到旧的navigator.getUserMedia
            return new Promise(function (resolve, reject) {
                getUserMedia.call(navigator, constraints, resolve, reject);
            });
        }
    }
    var constraints = { audio: false, video: {width: 720,height:720} };
    navigator.mediaDevices.getUserMedia(constraints)
        .then(function (stream) {
            var video = document.querySelector('video');
            // 旧的浏览器可能没有srcObject
            if ("srcObject" in video) {
                video.srcObject = stream;
            } else {
                //避免在新的浏览器中使用它，因为它正在被弃用。
                video.src = window.URL.createObjectURL(stream);
            }
            video.onloadedmetadata = function (e) {
                mysteam = stream;
                video.play();
            };
        })
        .catch(function (err) {
            console.log(err.name + ": " + err.message);
        });
};

TakePhotoFile.prototype.takephotoFile = function (that) {
    var canvas = document.getElementById("canvas"),
        context = canvas.getContext("2d"),
        video = document.getElementById("video");
    // 点击，canvas画图
    context.drawImage(video, 0, 0, 300, 300);
    // 获取图片base64链接
    var image = canvas.toDataURL('image/png');
    // 定义一个img
    // var img = new Image();
    // //设置属性和src
    // img.id = "imgBoxxx";
    // img.src = image;
    //将图片添加到页面中
    // document.body.appendChild(img);

    // base64转文件
    function dataURLtoFile(dataurl, filename) {
        var arr = dataurl.split(','), mime = arr[0].match(/:(.*?);/)[1],
            bstr = atob(arr[1]), n = bstr.length, u8arr = new Uint8Array(n);
        while (n--) {
            u8arr[n] = bstr.charCodeAt(n);
        }
        return new File([u8arr], filename, {type: mime});
    }
    mysteam.getTracks()[0].stop();
    return dataURLtoFile(image, 'aa.png')
};
var src = "";
function ChangeImg2File(imgsrc) {
    src = imgsrc
}
ChangeImg2File.prototype.constructor = ChangeImg2File;
ChangeImg2File.prototype.changeImg2File = function (that) {
    var canvas = document.createElement("canvas"),
        context = canvas.getContext("2d");
    canvas.width = 300;
    canvas.height = 300;
    // var img = new Image();
    // // console.log("src:  "+src);
    // img.src= src;
    // img.width = 300;
    // img.height = 300;
    var img = document.getElementById("random-head");
    // var imgW = img.width;
    // var imgH = img.height;
    // console.log("lallalalalal----:   ",imgW,imgH);
    context.drawImage(img,0,0,300,300);
    var image = canvas.toDataURL('image/png');
    // base64转文件
    function dataURLtoFile(dataurl, filename) {
        var arr = dataurl.split(','), mime = arr[0].match(/:(.*?);/)[1],
            bstr = atob(arr[1]), n = bstr.length, u8arr = new Uint8Array(n);
        while (n--) {
            u8arr[n] = bstr.charCodeAt(n);
        }
        return new File([u8arr], filename, {type: mime});
    }
    return dataURLtoFile(image, 'aa.png')
};


