package com.lucas.knot

import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription


class CallViewModel @ViewModelInject constructor(private val signalingRepository: SignalingRepository) : ViewModel() {

    val peerConnectionFactory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.builder().createPeerConnectionFactory()
    }
    var remotePeer: PeerConnection? = null
    var localPeer: PeerConnection? = null
    fun iceCandidateListener() = liveData(Dispatchers.IO) {
        signalingRepository.ICECandidateFlow.collect {
            Log.e("ice listener", "received ice candidate")
            // send over an icecandidate if there is one
            emit(IceCandidate(it.target, it.label.toInt(), it.candidate))
        }
    }

    fun signalAnswerListener() = liveData(Dispatchers.IO) {
        signalingRepository.signalAnswerFlow.collect {
            emit(SessionDescription(SessionDescription.Type.ANSWER, it.sdp))
        }
    }

    @FlowPreview
    @ExperimentalCoroutinesApi
    fun callOffer(offer: SignalOffer) {
        signalingRepository.callUser(offer)
    }

    @FlowPreview
    @ExperimentalCoroutinesApi
    fun callAnswer(answer: SignalAnswer) {
        signalingRepository.answerUser(answer)
    }

    @FlowPreview
    @ExperimentalCoroutinesApi
    fun updateIceCandidate(candidate: ICECandidate) {
        signalingRepository.updateIceCandidate(candidate)
    }


}