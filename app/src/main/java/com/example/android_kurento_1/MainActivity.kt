package com.example.android_kurento_1

import android.graphics.ImageFormat
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import fi.vtt.nubomedia.kurentoroomclientandroid.*
import fi.vtt.nubomedia.utilitiesandroid.LooperExecutor
import fi.vtt.nubomedia.webrtcpeerandroid.*
import org.webrtc.*
import android.widget.Button


class MainActivity : AppCompatActivity(), RoomListener, NBMWebRTCPeer.Observer {

    private lateinit var kurentoRoomAPI: KurentoRoomAPI
    private lateinit var nbmWebRTCPeer: NBMWebRTCPeer
    private lateinit var executor: LooperExecutor
    private lateinit var localRenderer: SurfaceViewRenderer
    private lateinit var remoteRenderer: SurfaceViewRenderer
    private lateinit var connectButton: Button // 추가: 버튼 변수 선언


    /*
    Kurento 서버와 WebSocket 연결을 설정하고, KurentoRoomAPI 인스턴스를 통해 서버에 연결
    이후 WebRTC 피어 설정을 위해 initializeWebRTC() 메소드를 호출
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize SurfaceViewRenderers for local and remote video
        localRenderer = findViewById(R.id.local_view)
        remoteRenderer = findViewById(R.id.remote_view)
        connectButton = findViewById(R.id.connect_button); // 수정: 버튼 초기화 추가


        val eglBase = EglBase.create()
        localRenderer.init(eglBase.eglBaseContext, null)
        remoteRenderer.init(eglBase.eglBaseContext, null)

        executor = LooperExecutor()
        executor.requestStart()

        // SSL 인증서 무시 설정 추가
        SSLHelper.trustAllCertificates();

        // 버튼 클릭 리스너 설정
        connectButton.setOnClickListener {
            //print("setOnCliskListener 수행")
            Log.d("MainActivity", "Button 클릭")
            // 서버 연결 수행
            initializeWebSocketAndWebRTC()
        }

    }

    /*
      서버에 WebSocket 연결 후 WebRTC 초기화
      */
    private fun initializeWebSocketAndWebRTC() {

        // Initialize KurentoRoomAPI with WebSocket URI
        val wsUri = "wss://10.0.2.2:8443/groupcall"
        kurentoRoomAPI = KurentoRoomAPI(executor, wsUri, this)
        kurentoRoomAPI.connectWebSocket()


        Log.d("MainActivity", "connectWebSocket() 수행 완료")
        // Initialize WebRTC
        initializeWebRTC()
    }


    /*
    WebRTC 관련 설정을 진행. 여기서 NBMMediaConfiguration을 생성하여 영상의 해상도, 오디오 코덱, 카메라 방향 등의 WebRTC 세부 설정을 정의
     */
    private fun initializeWebRTC() {

        // WebRTC Media Configuration
        val mediaConfiguration = NBMMediaConfiguration(
            NBMMediaConfiguration.NBMRendererType.OPENGLES,
            NBMMediaConfiguration.NBMAudioCodec.OPUS,
            0, NBMMediaConfiguration.NBMVideoCodec.VP8, 0,
            NBMMediaConfiguration.NBMVideoFormat(640, 480, ImageFormat.YUV_420_888, 30.0),
            NBMMediaConfiguration.NBMCameraPosition.FRONT
        )

        nbmWebRTCPeer = NBMWebRTCPeer(mediaConfiguration, this, localRenderer, this)
        nbmWebRTCPeer.initialize()

        Log.d("MainActivity", "initializeWebRTC 수행 완료")

        //onRoomConnected()
    }

    /*
    Kurento 서버와의 WebSocket이 성공적으로 연결되었을 때 호출
    이 메소드에서는 방에 참가하는 작업을 수행
    kurentoRoomAPI.sendJoinRoom()을 호출하여 참가자의 이름과 방 이름을 서버로 보냄
     */
    override fun onRoomConnected() {

        kurentoRoomAPI.sendJoinRoom("jin", "tae", true, 123)

        Log.d("MainActivity", "onRoomConnected 수행 완료")
    }


    /*
     sendJoinRoom() 호출에 대한 응답을 처리
     서버로부터 방 참가 응답을 받으면 generateOffer() 메소드를 통해 SDP 제안을 생성하고, Kurento 서버에 영상을 송출할 준비를 합니다.
     */
    override fun onRoomResponse(response: RoomResponse) {
        if (response.id == 123) {
            Log.d("KurentoRoom", "Successfully connected to the room!")
            // Now publish video to the room
            nbmWebRTCPeer.generateOffer("local", true)
        }
    }

    /*
    다른 참가자의 접속 및 ICE 후보에 대한 이벤트를 처리
    다른 참가자가 방에 참가했을 때, 또는 ICE 후보가 생성되었을 때 서버에서 받은 알림을 기반으로 WebRTC 연결을 설정
     */
    override fun onRoomNotification(notification: RoomNotification) {
        when (notification.method) {
            RoomListener.METHOD_PARTICIPANT_JOINED -> {
                Log.d("KurentoRoom", "New participant joined")
            }
            RoomListener.METHOD_ICE_CANDIDATE -> {
                val candidate = IceCandidate(
                    notification.getParam("candidate").toString(),
                    notification.getParam("sdpMid").toString().toInt(),
                    notification.getParam("sdpMLineIndex").toString()
                )
                nbmWebRTCPeer.addRemoteIceCandidate(candidate, "remoteParticipant")
            }
        }
    }


    // Handle errors
    override fun onRoomError(error: RoomError) {
        Log.e("KurentoRoom", error.toString())
    }

    override fun onRoomDisconnected() {
        Log.d("KurentoRoom", "Disconnected from the room")
    }

    override fun onInitialize() {
        Log.d("WebRTC", "WebRTC initialized")
    }

    /*
    onLocalSdpOfferGenerated() 메소드는 SDP 제안(offer)을 생성한 후 Kurento 서버로 전송
    WebRTC 피어 간의 멀티미디어 연결을 설정하는 데 필요한 메타데이터
     */
    override fun onLocalSdpOfferGenerated(localSdpOffer: SessionDescription?, connection: NBMPeerConnection?) {
        kurentoRoomAPI.sendPublishVideo(localSdpOffer?.description, false, 127)
    }

    // Other necessary WebRTC observer methods
    override fun onLocalSdpAnswerGenerated(localSdpAnswer: SessionDescription?, connection: NBMPeerConnection?) {
        // Handle local SDP answer if needed
    }

    override fun onIceCandidate(localIceCandidate: IceCandidate?, connection: NBMPeerConnection?) {
        kurentoRoomAPI.sendOnIceCandidate("MyUsername",
            localIceCandidate?.sdp, localIceCandidate?.sdpMid, localIceCandidate?.sdpMLineIndex.toString(), 126)

    }

    override fun onIceStatusChanged(state: PeerConnection.IceConnectionState?, connection: NBMPeerConnection?) {
        // Handle ICE status changes
        Log.d("WebRTC", "ICE status changed: $state")
    }

    override fun onRemoteStreamAdded(stream: MediaStream?, connection: NBMPeerConnection?) {
        // Attach remote stream to the renderer
        nbmWebRTCPeer.attachRendererToRemoteStream(remoteRenderer, stream)
        Log.d("WebRTC", "Remote stream added")
    }

    override fun onRemoteStreamRemoved(stream: MediaStream?, connection: NBMPeerConnection?) {
        // Handle remote stream removal
        Log.d("WebRTC", "Remote stream removed")
    }

    override fun onPeerConnectionError(error: String?) {
        Log.e("WebRTC", "Peer connection error: $error")
    }

    override fun onDataChannel(dataChannel: DataChannel?, connection: NBMPeerConnection?) {
        Log.d("WebRTC", "Data channel opened")
    }

    override fun onBufferedAmountChange(l: Long, connection: NBMPeerConnection?, channel: DataChannel?) {}
    override fun onStateChange(connection: NBMPeerConnection?, channel: DataChannel?) {}
    override fun onMessage(buffer: DataChannel.Buffer?, connection: NBMPeerConnection?, channel: DataChannel?) {}

}



