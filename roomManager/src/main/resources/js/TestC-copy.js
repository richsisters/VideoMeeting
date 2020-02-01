function DashType(url) {
    this.url = url;
    this.player = dashjs.MediaPlayer().create();
}
DashType.prototype.constructor = DashType;
DashType.prototype.initialize = function (that) {
    var st;
    st = window.setInterval(function () {
            // console.log(document.querySelector("#videoplayer"));
            // document.addEventListener("DOMContentLoaded",function(){
        if(document.querySelector("#videoplayer")!=null){
            that.player.initialize(document.querySelector("#videoplayer"), that.url, false);
            that.player.updateSettings({ 'streaming': { 'lowLatencyEnabled': true }});
            that.player.updateSettings({
                'streaming': {
                    // 'liveDelay': targetLatency,
                    'liveCatchUpMinDrift': 30,
                    'liveCatchUpPlaybackRate': 1.5
                }
            });
            clearInterval(st);
        }

            // })
    },500);

};
DashType.prototype.reset = function (that) {
    that.player.reset();
};

// window.addEventListener('resize', () => {
//     const activeElement = document.activeElement
//     if (activeElement.tagName === 'input' || activeElement.tagName === 'textarea') {
//         setTimeout(() => {
//             activeElement.scrollIntoView()
//         }, 100)
//     }
// });

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