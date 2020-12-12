package com.lucas.knot

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel

class ChatDetailViewModel @ViewModelInject constructor() : ViewModel() {
    lateinit var selectedChat: Chat
}