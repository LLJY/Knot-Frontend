package com.lucas.knot

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SelectUserViewModel @ViewModelInject constructor(private val userRepository: UserRepository) :
    ViewModel() {
    var selectedUserMutableLiveData: MutableLiveData<String> = MutableLiveData()

    val selectedUserObservable: LiveData<String> get() = selectedUserMutableLiveData

    fun getAllUsers(): List<LiveData<UserInfo>> {
        return userRepository.getAllUsers()
    }
}