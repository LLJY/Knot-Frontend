package com.lucas.knot

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.properties.Delegates

class ChatListViewModel @ViewModelInject constructor(
    private val advertisingRepository: AdvertisingRepository,
    val userRepository: UserRepository,
    private val signalingRepository: SignalingRepository,
    val chatRepository: ChatRepository,
    private val firebaseAuth: FirebaseAuth,
    private val notificationRepository: NotificationRepository,
    private val messaging: FirebaseMessaging
) : ViewModel() {
    var userId: String = ""
    var isFirstUser by Delegates.notNull<Boolean>()
    private val chatsMutableLiveData: MutableLiveData<List<Chat>> = MutableLiveData()

    val chatLiveData: LiveData<List<Chat>> get() = chatsMutableLiveData
    suspend fun getChats() = withContext(Dispatchers.IO) {
        if (isFirstUser) {
            chatsMutableLiveData.postValue(chatRepository.getAllChats(firebaseAuth.currentUser!!.uid))
        } else {
            chatsMutableLiveData.postValue((chatRepository.getAllMessagesWithDb(firebaseAuth.currentUser!!.uid)))
        }
        /// listen to the chat event stream
        chatRepository.eventStream(firebaseAuth.currentUser!!.uid)
        chatRepository.newMessagesFlow.collect {
            // add the additional message to the correct chat and inform activity
            val chatList = chatsMutableLiveData.value
            if (chatList != null) {
                val chatIndex = chatList.indexOf(chatList.first { chat -> chat.id == it.first })
                chatList[chatIndex].messages.add(it.second)
                chatsMutableLiveData.postValue(chatList)
            }
        }
    }

    /**
     * Initiates the event listener for calls
     */
    fun signalOfferListener() = liveData(Dispatchers.IO) {
        signalingRepository.signalOfferFlow.collect {
            emit(it)
        }
    }

    @FlowPreview
    @ExperimentalCoroutinesApi
    fun startSignalEventListener() {
        signalingRepository.startEventListener()
    }

    fun updateNotificationToken() = liveData {
        emit(
            notificationRepository.updateNotificationId(
                firebaseAuth.currentUser!!.uid,
                messaging.token.await()
            )
        )
    }

    fun getAdvert() = liveData(Dispatchers.IO) {
        emit(advertisingRepository.getAdvert())
    }
}