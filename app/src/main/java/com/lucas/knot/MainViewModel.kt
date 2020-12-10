package com.lucas.knot

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers

class MainViewModel @ViewModelInject constructor(private val firebaseAuth: FirebaseAuth) :
    ViewModel() {
    var phoneNumber: String = ""
    fun checkLoggedIn() = liveData(Dispatchers.IO) { emit(firebaseAuth.currentUser != null) }
}