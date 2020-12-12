package com.lucas.knot

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers

class ChatListViewModel @ViewModelInject constructor(private val chatRepository: ChatRepository, private val firebaseAuth: FirebaseAuth) : ViewModel() {
    var userId: String = ""
    fun getChats() = liveData(Dispatchers.IO) {
        //TODO conditionally call different functions for get chats from local database
        emit(chatRepository.getAllChats(firebaseAuth.currentUser!!.uid))
    }
}