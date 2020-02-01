var webRtcPeer;

var isNegotiating = false;
// handle process sdp
function handleProcessSdpAnswer(jsonMessage) {
    console.log("[handleProcessSdpAnswer] SDP Answer from Kurento, process in WebRTC Peer");
    webRtcPeer.processAnswer(jsonMessage.sdpAnswer, (err) => {
        if (isNegotiating) {
            console.log("SKIP nested negotiations");
            return;
        }
        isNegotiating = true;
        if (err) {
            console.error("[handleProcessSdpAnswer] " + err);
            return;
        }
        console.log("[handleProcessSdpAnswer] SDP Answer ready; start remote video");
    });
}

// ADD_ICE_CANDIDATE -----------------------------------------------------------

function handleAddIceCandidate(jsonMessage) {
    webRtcPeer.addIceCandidate(jsonMessage.candidate, (err) => {
        if (err) {
            console.error("[handleAddIceCandidate] " + err);
            return;
        }
    });
}

//handler webRtc message
function messageHandler(data) {
    console.log(data);
    let parsedMessage = JSON.parse(data);
    switch (parsedMessage.id) {
        case 'PROCESS_SDP_ANSWER':
            handleProcessSdpAnswer(parsedMessage);
            break;

        case 'ADD_ICE_CANDIDATE':
            handleAddIceCandidate(parsedMessage);
            break;

        default:
            console.error('Unrecognized message', parsedMessage);
    }
}

function send(message) {
    var jsonMessage = JSON.stringify(message);
    ScalaWebSocket.sendMessage(jsonMessage);
}

//定义创建webrtc
function webRtcStart(idString) {
    uiLocalVideo = document.getElementById('uiLocalVideo');
    uiRemoteVideo = document.getElementById('uiRemoteVideo');
    let iceservers = {
        "iceServers": [
            {
                urls: "stun:123.56.108.66:41640"
            },
            {
                urls: ["turn:123.56.108.66:41640"],
                username: "hu",
                credential: "123456"
            }
        ]
    };
    console.log("[start] Create WebRtcPeerSendrecv");

    const options = {
        localVideo: uiLocalVideo,
        remoteVideo: uiRemoteVideo,
        mediaConstraints: {audio: true, video: true},
        onicecandidate: (candidate) => send({
            id: 'ADD_ICE_CANDIDATE',
            candidate: candidate,
        }),
        configuration: iceservers
    };
    webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
        function (err) {
            console.log("[start/WebRtcPeerSendrecv] Created; start local video");

            console.log("[start/WebRtcPeerSendrecv] Generate SDP Offer");
            webRtcPeer.generateOffer((err, sdpOffer) => {

                send({
                    id: idString,
                    sdpOffer: sdpOffer
                });

                console.log("[start/WebRtcPeerSendrecv/generateOffer] Done!");
            });
        });
}

//定义停止webrtc
function webRtcStop() {
    if(webRtcPeer){
        console.log("webrtc stoping...");
        webRtcPeer.dispose();
        webRtcPeer = null;
    }else{
        console.log("webrtc not start");
    }
}
