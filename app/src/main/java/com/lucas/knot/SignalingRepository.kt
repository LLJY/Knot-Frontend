package com.lucas.knot

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import services.SignalingGrpc
import services.SignalingOuterClass
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SignalAnswer(var type: String, var name: String, var sdp: String, var userId: String)

@Serializable
data class SignalOffer(var type: String?, var name: String?, var sdp: String?, var userId: String?) : Parcelable {
    constructor(parcel: Parcel) : this(
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(type)
        parcel.writeString(name)
        parcel.writeString(sdp)
        parcel.writeString(userId)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<SignalOffer> {
        override fun createFromParcel(parcel: Parcel): SignalOffer {
            return SignalOffer(parcel)
        }

        override fun newArray(size: Int): Array<SignalOffer?> {
            return arrayOfNulls(size)
        }
    }
}

data class ICECandidate(var type: String, var label: String, var target: String, var candidate: String)

// enum for call status, should have more in the future
enum class CallStatus {
    ONGOING,
    ENDED
}

@ExperimentalCoroutinesApi
@FlowPreview
@Singleton
class SignalingRepository @Inject constructor(private val signalingStub: SignalingGrpc.SignalingStub, private val auth: FirebaseAuth) {
    private val signalOfferBroadcastChannel = ConflatedBroadcastChannel<SignalOffer>()
    private val signalAnswerBroadcastChannel = ConflatedBroadcastChannel<SignalAnswer>()
    private val ICECandidateBroadcastChannel = ConflatedBroadcastChannel<ICECandidate>()
    private val callStatusBroadcastChannel = ConflatedBroadcastChannel<CallStatus>()
    var requestStream: StreamObserver<SignalingOuterClass.Signal>? = null
    val signalOfferFlow get() = signalOfferBroadcastChannel.asFlow()
    val signalAnswerFlow get() = signalAnswerBroadcastChannel.asFlow()
    val ICECandidateFlow get() = ICECandidateBroadcastChannel.asFlow()
    val callStatusFlow get() = callStatusBroadcastChannel.asFlow()
    private var peerUserId: String? = null

    fun startEventListener() {
        val initSenderData = SignalingOuterClass.SenderData.newBuilder()
                .setIsInit(true)
                .setUserid(auth.currentUser!!.uid)
                .build()

        requestStream = signalingStub.callStream(object : StreamObserver<SignalingOuterClass.Signal> {
            override fun onNext(value: SignalingOuterClass.Signal?) {
                if (value != null) {
                    if (value.senderInfo.userid != null) {
                        peerUserId = value.senderInfo.userid
                    }
                    Log.e("on next", peerUserId)
                    if (value.hasSignalOffer()) {
                        // create the local class and send it to the broadcast channel
                        val offer = SignalOffer(value.signalOffer.type, value.signalOffer.name, value.signalOffer.offer, value.senderInfo.userid)
                        GlobalScope.launch {
                            signalOfferBroadcastChannel.send(offer)
                        }
                    } else if (value.hasSignalAnswer()) {
                        val offer = SignalAnswer(value.signalAnswer.type, value.signalAnswer.name, value.signalAnswer.answer, value.senderInfo.userid)
                        GlobalScope.launch {
                            signalAnswerBroadcastChannel.send(offer)
                        }
                    } else if (value.hasICECandidateRequest()) {
                        val candidate = ICECandidate(value.iceCandidateRequest.type, value.iceCandidateRequest.label, value.iceCandidateRequest.target, value.iceCandidateRequest.candidate)
                        GlobalScope.launch {
                            ICECandidateBroadcastChannel.send(candidate)
                        }
                    } else if (value.hasLeave()) {
                        GlobalScope.launch {
                            callStatusBroadcastChannel.send(CallStatus.ENDED)
                        }
                    }
                }
            }

            override fun onError(t: Throwable?) {
                Log.e("call event stream", t.toString())
            }

            override fun onCompleted() {
                TODO()
            }

        })
        val signal = SignalingOuterClass.Signal.newBuilder()
                .setSenderInfo(SignalingOuterClass.SenderData.newBuilder()
                        .setIsInit(true)
                        .setUserid(auth.currentUser!!.uid)
                        .build()).build()
        // send initial request to tell service we are online
        requestStream?.onNext(signal)
    }

    /**
     * Sends a call offer to the specified user id
     */
    fun callUser(offer: SignalOffer) {
        peerUserId = offer.userId
        val signal = SignalingOuterClass.Signal
                .newBuilder()
                .setSignalOffer(SignalingOuterClass.SignalOffer.newBuilder()
                        .setType(offer.type)
                        .setName(offer.name)
                        .setOffer(offer.sdp)
                        .build())
                .setRecieverId(offer.userId)
                .setSenderInfo(SignalingOuterClass.SenderData.newBuilder()
                        .setUserid(auth.currentUser!!.uid)
                        .setIsInit(false)
                        .build())
                .build()
        requestStream?.onNext(signal)
    }

    /**
     * Sends a call answer to the specified user id
     */
    fun answerUser(answer: SignalAnswer) {
        peerUserId = answer.userId
        Log.e("answer user", "attempting to answer... ${answer.userId}")
        val signal = SignalingOuterClass.Signal
                .newBuilder()
                .setSignalAnswer(SignalingOuterClass.SignalAnswer.newBuilder()
                        .setType(answer.type)
                        .setName(answer.name)
                        .setAnswer(answer.sdp)
                        .build())
                .setRecieverId(answer.userId)
                .setSenderInfo(SignalingOuterClass.SenderData.newBuilder()
                        .setUserid(auth.currentUser!!.uid)
                        .setIsInit(false)
                        .build())
                .build()
        requestStream!!.onNext(signal)
    }

    fun updateIceCandidate(candidate: ICECandidate) {
        Log.e("updating ice", "attempting to update $peerUserId")
        // send the ice candidate over
        val signal = SignalingOuterClass.Signal.newBuilder()
                .setICECandidateRequest(SignalingOuterClass.ICECandidate.newBuilder()
                        .setTarget(candidate.target)
                        .setLabel(candidate.label)
                        .setType(candidate.type)
                        .setCandidate(candidate.candidate).build())
                .setRecieverId(peerUserId)
                .setSenderInfo(SignalingOuterClass.SenderData.newBuilder()
                        .setUserid(auth.currentUser!!.uid)
                        .setIsInit(false)
                        .build()).build()
        requestStream!!.onNext(signal)
    }
}