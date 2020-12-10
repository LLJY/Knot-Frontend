package com.lucas.knot

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import kotlinx.coroutines.Dispatchers

class LoginPhoneNumberViewModel @ViewModelInject constructor(private val userRepository: UserRepository) : ViewModel() {
    fun requestOTP(phoneNumber: String): LiveData<Int> = liveData(Dispatchers.IO) { emit(userRepository.requestOTP(phoneNumber)) }
}