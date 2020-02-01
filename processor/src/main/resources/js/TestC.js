require.config({
    paths: {
        "rtmp-streamer" : "./rtmp-streamer.min"
    }
});

function PublishType(url, name) {
    this.url = url;
    this.name = name;
}

PublishType.prototype.constructor = PublishType;
PublishType.prototype.publish = function(that) {
    require(["rtmp-streamer"], function (RtmpStreamer){
        var streamer = new RtmpStreamer(document.getElementById('rtmp-streamer'));
        streamer.setCamFrameInterval(20);
        streamer.setCamMode(1280,720,24);
        streamer.setScreenSize(672,378);
        streamer.setScreenPosition(-85,0);
        streamer.setCamQuality(0,50);
        streamer.publish(that.url,that.name);
    });

};

function PlayerType(url) {
    this.url = url;
}

PlayerType.prototype.constructor = PlayerType;
PlayerType.prototype.initialize = function (that) {
    var player = dashjs.MediaPlayer().create();
    player.initialize(document.querySelector("#videoplayer"), that.url, true);
    player.updateSettings({
        'streaming':
          {
              'liveDelay': 6
          }
    });
    var controlbar = new ControlBar(player);
    controlbar.initialize();

    // setInterval( function() {
    //     var d = new Date();
    //     document.querySelector("#videoDelay").innerHTML = Math.round((d.getTime()/1000) - Number(player.timeAsUTC())) + "s";
    //     document.querySelector("#videoBuffer").innerHTML = player.getBufferLength()+ "s";
    // },1000);
};



function DisconnectType() {}

DisconnectType.prototype.constructor = DisconnectType;
DisconnectType.prototype.disconnect = function() {
    require(["rtmp-streamer"], function (RtmpStreamer){
        var streamer = new RtmpStreamer(document.getElementById('rtmp-streamer'));
        streamer.disconnect();
    });
};