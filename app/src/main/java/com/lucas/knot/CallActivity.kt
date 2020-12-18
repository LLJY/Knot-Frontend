package com.lucas.knot

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lucas.knot.databinding.ActivityCallBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import org.webrtc.*


@AndroidEntryPoint
class CallActivity : AppCompatActivity() {
    private val RC_CALL = 111
    val VIDEO_TRACK_ID = "ARDAMSv0"
    val VIDEO_RESOLUTION_WIDTH = 1280
    val VIDEO_RESOLUTION_HEIGHT = 720
    val FPS = 30
    private val binding: ActivityCallBinding by lazy { ActivityCallBinding.inflate(layoutInflater) }
    private lateinit var factory: PeerConnectionFactory
    var audioConstraints: MediaConstraints? = null
    var videoConstraints: MediaConstraints? = null
    var sdpConstraints: MediaConstraints? = null
    var videoSource: VideoSource? = null
    lateinit var localVideoTrack: VideoTrack
    var audioSource: AudioSource? = null
    var localAudioTrack: AudioTrack? = null
    var surfaceTextureHelper: SurfaceTextureHelper? = null
    private val viewModel: CallViewModel by viewModels()
    private var peerConnection: PeerConnection? = null
    private var rootEglBase: EglBase = EglBase.create()
    private var videoTrackFromCamera: VideoTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        start()
        val signalOffer = intent.getParcelableExtra<SignalOffer>("SIGNAL_OFFER")
        val startCallUserId = intent.getStringExtra("START_CALL")
        if (signalOffer != null) {
            // we are the receivers
            peerConnection?.setRemoteDescription(
                    SimpleSdpObserver(), SessionDescription(
                    SessionDescription.Type.OFFER,
                    signalOffer.sdp
            )
            )
            Log.e("signal offer user", signalOffer.userId)
            doAnswer(signalOffer.userId!!)
        } else if (startCallUserId != null) {
            // call the user and wait for them to answer
            doCall(startCallUserId)
            // listen if the call has been answered
            viewModel.signalAnswerListener().observe(this) {
                peerConnection?.setRemoteDescription(SimpleSdpObserver(), it)
            }
        }

        viewModel.iceCandidateListener().observe(this) {
            Log.e("ice", "adding ice candidate")
            // add the candidate
            peerConnection?.addIceCandidate(it)
        }

    }

    /**
     * calls the specific user id
     */
    @ExperimentalCoroutinesApi
    @FlowPreview
    private fun doCall(userId: String) {
        val sdpMediaConstraints = MediaConstraints()
        sdpMediaConstraints.mandatory.add(
                MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        )
        sdpMediaConstraints.mandatory.add(
                MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
        )
        peerConnection!!.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d("doCall", "onCreateSuccess: " + sessionDescription.type.canonicalForm())
                peerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                viewModel.callOffer(
                        SignalOffer(
                                sessionDescription.type.canonicalForm(),
                                userId,
                                sessionDescription.description,
                                userId
                        )
                )
            }
        }, sdpMediaConstraints)
    }

    private fun doAnswer(userId: String) {
        val sdpMediaConstraints = MediaConstraints()
        peerConnection!!.createAnswer(object : SimpleSdpObserver() {

            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.e("sdp success", "do answer")
                peerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                viewModel.callAnswer(
                        SignalAnswer(
                                "answer",
                                userId,
                                sessionDescription.description,
                                userId
                        )
                )
            }

            override fun onSetFailure(s: String) {
                Log.e("sdpFail", s.toString())
            }

            override fun onCreateFailure(s: String) {
                super.onCreateFailure(s)
                Log.e("sdpFail", s.toString())
                println(s.toString())
            }
        }, sdpMediaConstraints)
    }

    private fun start() {
        // initialize everything!
        initializeSurfaceViews()
        initializePeerConnectionFactory()
        createVideoTrackFromCameraAndShowIt()
        initializePeerConnections()
        startStreamingVideo()
    }

    private fun initializeSurfaceViews() {
        // set the primary view
        binding.rtcSurfaceView.init(rootEglBase.eglBaseContext, null)
        binding.rtcSurfaceView.setEnableHardwareScaler(true)
        binding.rtcSurfaceView.setMirror(true)
        //set the secondary view (other person)
        binding.peerSurfaceView.init(rootEglBase.eglBaseContext, null)
        binding.peerSurfaceView.setEnableHardwareScaler(true)
        binding.peerSurfaceView.setMirror(true)
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(applicationContext).createInitializationOptions()
        val encoder = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)
        PeerConnectionFactory.initialize(options)
        factory = PeerConnectionFactory.builder().setVideoEncoderFactory(encoder)
                .setVideoDecoderFactory(decoder)
                .createPeerConnectionFactory()
    }

    private fun startStreamingVideo() {
        val mediaStream = factory.createLocalMediaStream("ARDAMS")
        mediaStream.addTrack(videoTrackFromCamera)
        mediaStream.addTrack(localAudioTrack)
        peerConnection!!.addStream(mediaStream)
    }

    private fun createVideoTrackFromCameraAndShowIt() {
        audioConstraints = MediaConstraints()
        val videoCapturer = createVideoCapturer()
        var videoSource = factory.createVideoSource(false)
        if (videoCapturer != null) {
            surfaceTextureHelper =
                    SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
            videoSource = factory.createVideoSource(videoCapturer.isScreencast)
            videoCapturer.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
            videoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS)
        }
        videoTrackFromCamera = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        videoTrackFromCamera!!.setEnabled(true)
        videoTrackFromCamera!!.addSink(binding.rtcSurfaceView)

        //create an AudioSource instance
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("101", audioSource)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        return if (useCamera2()) {
            createCameraCapturer(Camera2Enumerator(this))
        } else {
            createCameraCapturer(Camera1Enumerator(true))
        }
    }

    /**
     * Create the camera capture
     */
    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }

    private fun initializePeerConnections() {
        peerConnection = createPeerConnection(factory)
    }

    fun createPeerConnection(factory: PeerConnectionFactory): PeerConnection? {
        val iceServers: ArrayList<PeerConnection.IceServer> = ArrayList()
        iceServers.add(PeerConnection.IceServer("stun: stun.l.google.com:19302"))
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        val pcConstraints = MediaConstraints()
        val pcObserver: PeerConnection.Observer = object : PeerConnection.Observer {
            override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
                Log.d("CallViewModel", "onSignalingChange: ${signalingState.name}")
            }

            override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                Log.d("CallViewModel", "onIceConnectionChange: ")
            }

            override fun onIceConnectionReceivingChange(b: Boolean) {
                Log.d("CallViewModel", "onIceConnectionReceivingChange: ")
            }

            override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
                Log.d("CallViewModel", "onIceGatheringChange: ")
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                Log.d("CallViewModel", "onIceCandidate: ")
                val candidate = ICECandidate(
                        "candidate",
                        iceCandidate.sdpMLineIndex.toString(),
                        iceCandidate.sdpMid,
                        iceCandidate.sdp
                )
                viewModel.updateIceCandidate(candidate)
            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                Log.d("CallViewModel", "onIceCandidatesRemoved: ")
            }

            override fun onAddStream(mediaStream: MediaStream) {
                Log.d("CallViewModel", "onAddStream: " + mediaStream.videoTracks.size)
                // dispaly the video in the main thread
                lifecycleScope.launch(Dispatchers.Main) {
                    val remoteVideoTrack = mediaStream.videoTracks[0]
                    val remoteAudioTrack = mediaStream.audioTracks[0]
                    remoteAudioTrack.setEnabled(true)
                    remoteVideoTrack.setEnabled(true)
                    Log.e("remotetrack", remoteVideoTrack.state().toString())
                    remoteVideoTrack.addSink(binding.peerSurfaceView)
                }

            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                super.onTrack(transceiver)
                Log.d("CallViewModel", "onTrack: ")
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Log.d("CallViewModel", "onRemoveStream: ")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                Log.d("CallViewModel", "onDataChannel: ")
            }

            override fun onRenegotiationNeeded() {
                Log.d("CallViewModel", "onRenegotiationNeeded: ")
            }

            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                Log.d("CallViewModel", "onAddTrack: ")

//                // dispaly the video in the main thread
//                lifecycleScope.launch(Dispatchers.Main) {
//                    val remoteVideoTrack = p1?.get(0)?.videoTracks?.get(0)
//                    //val remoteAudioTrack = mediaStream.audioTracks[0]
//                    //remoteAudioTrack.setEnabled(true)
//                    remoteVideoTrack?.setEnabled(true)
//                    Log.e("remotetrack", p1?.size.toString())
//                    remoteVideoTrack?.addSink(binding.peerSurfaceView)
//                }
            }
        }
        return factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver)
    }

    private fun useCamera2(): Boolean {
        return Camera2Enumerator.isSupported(this)
    }


}