package com.lucas.knot

import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class ChatDetailViewModel @ViewModelInject constructor(private val signalingRepository: SignalingRepository, val chatRepository: ChatRepository, private val auth: FirebaseAuth, private val userRepository: UserRepository) : ViewModel() {
    lateinit var selectedChat: Chat
    private val chatMutableLiveData: MutableLiveData<Chat> = MutableLiveData()

    val chatLiveData get() = chatMutableLiveData

    suspend fun eventListener() = withContext(Dispatchers.IO) {
        chatRepository.newMessagesFlow.collect {
            // add the additional message to the correct chat and inform activity
            if (selectedChat.id == it.first) {
                // according to the rules of BLACK FUCKING MAGIC selectedChat gets updated automatically??????
                chatMutableLiveData.postValue(selectedChat)
                launch(Dispatchers.Main) {
                    readAllMessages()
                }
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

    fun readMessage(id: String) = liveData {
        emit(chatRepository.readMessage(id))
    }

    /**
     * send the server to tell it that the message has been read
     */
    fun readAllMessages() {
        val messages = selectedChat.messages
        try {
            messages.forEach { i ->
                // observe for userinfo and do not send it if the userid is the same as the sender's
                i.senderUser.observeForeverOnce {
                    if (i.messageStatus != MessageStatus.READ && it.userId != auth.currentUser!!.uid && i.message.isNotBlank()) {
                        viewModelScope.launch {
                            delay(100)
                            chatRepository.readMessage(i.id)
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("readAllMessages()", ex.toString())
        }
    }

    /**
     * send a message to the server
     */
    fun sendMessage(message: String, replyId: String?): LiveData<Boolean> {
        val messageModel = Message(UUID.randomUUID().toString(), replyId, null, false, MessageStatus.SENT, System.currentTimeMillis(), message, selectedChat.userInfo, userRepository.getUserInfo(auth.currentUser!!.uid))
        return liveData(Dispatchers.IO) {
            try {
                // if userInfo is null, send groupId instead
                if (selectedChat.userInfo != null) {
                    val id = chatRepository.sendMessage(
                        messageModel,
                        auth.currentUser!!.uid,
                        selectedChat.userInfo!!.value?.userId,
                        null
                    )
                    // if the chat id is not the same, update it. this is to ensure that the database id is synced up with view
                    if (selectedChat.id != id) {
                        selectedChat.id = id
                        chatMutableLiveData.postValue(selectedChat)
                    }
                } else {
                    chatRepository.sendMessage(messageModel, auth.currentUser!!.uid, selectedChat.userInfo!!.value?.userId, selectedChat.groupId)
                }
                selectedChat.messages.add(messageModel)
                // if nothing goes wrong, update the view with the message
                chatMutableLiveData.postValue(selectedChat)
                emit(true)
            } catch (ex: Exception) {
                Log.e("send msg", ex.toString())
                emit(false)
            }
        }
    }
}