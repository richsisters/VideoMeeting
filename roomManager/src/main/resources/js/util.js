var fullTag = 0
/** anchor page fullScreen **/
function fullScreen() {}
fullScreen.prototype.constructor = fullScreen();
fullScreen.prototype.a = function () {
    var de = document.getElementById("anchor-all");
    if(de.requestFullscreen){
        de.requestFullscreen();
    } else if (de.mozRequestFullScreen) {
        de.mozRequestFullScreen();
    } else if (de.webkitRequestFullScreen) {
        de.webkitRequestFullScreen();
    }
}
/** anchor page exit fullScreen **/
function exitFullScreen() {}
exitFullScreen.prototype.constructor = exitFullScreen();
exitFullScreen.prototype.b = function () {
    var de = document.getElementById("anchor-all");
    if(document.exitFullscreen){
        document.exitFullscreen();
    } else if (document.mozCancelFullScreen) {
        document.mozCancelFullScreen();
    } else if (document.webkitCancelFullScreen) {
        document.webkitCancelFullScreen();
    }
    // fullTag += 1;
    // de.setAttribute("style","padding:30px 30px 50px 30px");
};
//TODO maybe exist some problems
// window.onresize = function () {
//     if (!checkFull()){
//         fullTag += 1;
//         // var de = document.getElementById("anchor-all");
//         // if(fullTag%2===0) de.setAttribute("style","padding:30px 30px 50px 30px");
//         // else de.setAttribute("style","padding:0")
//         // console.log(fullTag)
//     }
//
// }
// function checkFull() {
//     var de = document.getElementById("anchor-all");
//     var isFull = de.fullscreenElement|| de.mozFullScreenElement  || de.webkitFullscreenElement  || de.msFullscreenElement  ;
//     //to fix : false || undefined == undefined
//     if (isFull === undefined) { isFull = false; }
//     return isFull;
// }
/** emoji js **/
var fetchOver = false;
var faceHtml = "";
var animalHtml = "";
var fruitOrFoodHtml = "";
function fetchEmoji(type) {
    if(!fetchOver){
        fetch('/VideoMeeting/roomManager/static/html/edata.json').then(data => data.json()).then(json =>{
            var cutFlag = 1;
            for( k in json){
                var emoji = json[k];
                if(emoji['char'].trim() === "animal-cut"){
                    cutFlag = 2;
                }
                if(emoji['char'].trim() === "fruit-cut"){
                    cutFlag = 3;
                }
                switch (cutFlag){
                    case 1:
                        faceHtml += '<li class="emoji-result verify-ict"> ' +
                            '<div class="emoji-char verify-ict" title=\"'+ emoji['char']+'\">'+emoji['char']+'</div> </li>'
                        break;
                    case 2:
                        if(emoji['char'].trim() === "animal-cut") break;
                        animalHtml += '<li class="emoji-result verify-ict"> ' +
                            '<div class="emoji-char verify-ict" title=\"'+ emoji['char']+'\">'+emoji['char']+'</div> </li>'
                        break;
                    case 3:
                        if(emoji['char'].trim() === "fruit-cut") break;
                        fruitOrFoodHtml += '<li class="emoji-result verify-ict"> ' +
                            '<div class="emoji-char verify-ict" title=\"'+ emoji['char']+'\">'+emoji['char']+'</div> </li>'
                        break;

                }
            }
            addHtml(type);
        });
        fetchOver = true;
    }else{
        addHtml(type);
    }
    
    document.removeEventListener('click',handleEmojiClick);
    document.addEventListener('click',handleEmojiClick)

}
function addHtml(type) {
    var emojiContainer = document.getElementById("emoji-container-in");
    emojiContainer.innerHTML = "";
    switch (type){
        case 1:
            emojiContainer.innerHTML = faceHtml.toString().replace("undefined","");
            break;
        case 2:
            emojiContainer.innerHTML = animalHtml.toString().replace("undefined","");
            break;
        case 3:
            emojiContainer.innerHTML = fruitOrFoodHtml.toString().replace("undefined","");
            break;

    }
}
function handleEmojiClick(event) {
    var inputText = document.getElementById("s-commment");
    if(event.target.classList.contains('emoji-char')){
        // console.log("---: "+inputText.value+" || "+event.target.getAttribute("title"))
        inputText.value += event.target.getAttribute("title");
    }
}
document.onclick = function (e) {
    var e = event || window.event;
    var elem = e.srcElement||e.target;

    while(elem)
    {

        if(elem.classList.contains("verify-ict"))
        {
            return;
        }else{
            elem = elem.parentNode;
            break;
        }

    }
    if(document.getElementById("danmuka-color")!==null) document.getElementById("danmuka-color").setAttribute("style","display:none");
    if(document.getElementById("emoji-container-out")!==null)document.getElementById("emoji-container-out").setAttribute("style","display:none")
};
//判断手机系统
function getOS() {
    var u = navigator.userAgent, app = navigator.appVersion;
    return !!u.match(/\(i[^;]+;( U;)? CPU.+Mac OS X/);
}
//分页
function pagePaginator(tagId,currentPage, perPageSize, totalPages) {
    // var st;
    // st = window.setInterval(function () {
    //     if(document.querySelector("#bp-3-element")!=null){
            var element = $('#'+tagId);
            var options = {
                bootstrapMajorVersion: 3,
                currentPage: currentPage,
                numberOfPages: perPageSize,
                totalPages: totalPages,
                onPageClicked: function(e,originalEvent,type,page){
                    // console.log("page: "+page);
                }
            }
            element.bootstrapPaginator(options);
    //         clearInterval(st);
    //     }
    // },100);
}
//加载流式MP4
var assetURL = 'http://nickdesaulniers.github.io/netfix/demo/frag_bunny.mp4';
// Need to be specific for Blink regarding codecs
// ./mp4info frag_bunny.mp4 | grep Codec
var mimeCodec = 'video/mp4; codecs="avc1.42E01E, mp4a.40.2"';
var video = document.querySelector('video');

function playMp4(mp4Url) {
    // assetURL = mp4Url;

    if ('MediaSource' in window && MediaSource.isTypeSupported(mimeCodec)) {
        var mediaSource = new MediaSource();
        //console.log(mediaSource.readyState); // closed
        video.src = URL.createObjectURL(mediaSource);
        mediaSource.addEventListener('sourceopen', sourceOpen);
    } else {
        console.error('Unsupported MIME type or codec: ', mimeCodec);
    }
}

function sourceOpen (_) {
    //console.log(this.readyState); // open
    var mediaSource = this;
    var sourceBuffer = mediaSource.addSourceBuffer(mimeCodec);
    fetchAB(assetURL, function (buf) {
        sourceBuffer.addEventListener('updateend', function (_) {
            mediaSource.endOfStream();
            video.play();
            //console.log(mediaSource.readyState); // ended
        });
        sourceBuffer.appendBuffer(buf);
    });
};

function fetchAB (url, cb) {
    console.log(url);
    var xhr = new XMLHttpRequest;
    xhr.open('get', url);
    xhr.responseType = 'arraybuffer';
    xhr.onload = function () {
        cb(xhr.response);
    };
    xhr.send();
};