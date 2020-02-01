var CM = null

function cmInit() {
    var CM = new CommentManager(document.getElementById('commentCanvas'));
    CM.init();

    // 启动播放弹幕（在未启动状态下弹幕不会移动）
    CM.start();

    // 开放 CM 对象到全局这样就可以在 console 终端里操控
    window.CM = CM;
}

function clearMsg() {
   if(CM != null) CM.clear();
}

function sendMsg(cmtArr,mode,dur) {
    for (var i=0; i<cmtArr.length; i++) {
        var cmtItem = cmtArr[i];

        // 字幕的节点内容
        cmtItem.mode = mode;
        cmtItem.dur = dur;
        console.log("cmt : "+cmtItem.text)
        CM.send(cmtItem);
    }
}

function setCmtData(str,isMe,cl) {
    // console.log("121212----: "+str);
    var color = ['800000', '008000', '808000', '800080', '00FFFF', 'FFFFFF', 'F0FFFF', '8A2BE2', 'FF0000', 'FF00FF'];
    // [Math.floor(Math.random() * 10 )]
    var cmtArr = [];
    var dur = Math.floor(Math.random()*4000 + 4000);
    if(isMe === 1){
        if(str.substring(0,1) === "+"){
            cmtArr = [{"text":str.substring(1),"color":cl,"size":25,
                'motion':[
                    {
                    "size": { "from": 25, "to": 50, "dur": 2000, "delay": 0}
                    }]
            }];
            sendMsg(cmtArr, 4, 2500);
        }else{
            cmtArr = [{"text":str,"color":cl,"size":25,"border":true}];
            sendMsg(cmtArr, 1,dur);
        }
    }else {
        if(str.substring(0,1) === "+"){
            cmtArr = [{"text":str.substring(1),"color":cl,"size":25,
                'motion':[
                    {
                        "size": { "from": 25, "to": 50, "dur": 2000, "delay": 0}
                    }]
            }];
            sendMsg(cmtArr, 4,2500);
        }else{
            cmtArr = [{"text":str,"color":cl,"size":25}];
            sendMsg(cmtArr, 1,dur);
        }
    }
}
// 修改ccl源文件以支持图片格式弹幕
function sendGifts(str) {
    var cmtArr = [];
    var dur = Math.floor(Math.random()*4000 + 4000);
    if(str.indexOf('西蓝花') !== -1){
        cmtArr = [{"text":str,"imgUrl":'/VideoMeeting/roomManager/static/img/gifts/broccoli.png',"color":'808000',"size":25,"gift":true}];
    }
    else if(str.indexOf('棒棒糖') !== -1 ){
        cmtArr = [{"text":str,"imgUrl":'/VideoMeeting/roomManager/static/img/gifts/lollipop.png',"color":'808000',"size":25,"gift":true}];
    }
    else if(str.indexOf('雪糕') !== -1 ){
        cmtArr = [{"text":str,"imgUrl":'/VideoMeeting/roomManager/static/img/gifts/ice-cream.png',"color":'808000',"size":25,"gift":true}];
    }
    else if(str.indexOf('面包') !== -1 ){
        cmtArr = [{"text":str,"imgUrl":'/VideoMeeting/roomManager/static/img/gifts/bread.png',"color":'808000',"size":25,"gift":true}];
    }
    else if(str.indexOf('果汁') !== -1 ){
        cmtArr = [{"text":str,"imgUrl":'/VideoMeeting/roomManager/static/img/gifts/juice.png',"color":'808000',"size":25,"gift":true}];
    }
    else if(str.indexOf('蛋糕') !== -1 ){
        cmtArr = [{"text":str,"imgUrl":'/VideoMeeting/roomManager/static/img/gifts/cake.png',"color":'808000',"size":25,"gift":true}];
    }else{
        cmtArr = [{"text":str,"imgUrl":str,"color":'808000',"size":25}];
    }
    sendMsg(cmtArr, 1,dur);
}

