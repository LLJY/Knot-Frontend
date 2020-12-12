package com.lucas.knot

import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await


class OneTimePasswordViewModel @ViewModelInject constructor(private val userRepository: UserRepository, @Assisted private val savedStateHandle: SavedStateHandle, private val firebaseAuth: FirebaseAuth) : ViewModel() {
    // to set the time when the OTP was requested so the next activity can initiate a countdown
    var OTP: Int = 0
    fun setOTPTime(timeRemaining: Long) {
        savedStateHandle.set("REQUESTED_TIME", System.currentTimeMillis())
        savedStateHandle.set("OTP_DEADLINE", System.currentTimeMillis() + (timeRemaining * 1000))
    }

    fun calculateSecondsRemaining() = liveData(Dispatchers.IO) {
        // get the time left from savedInstanceState
        val deadline = savedStateHandle.get<Long>("OTP_DEADLINE")
        val curTime = System.currentTimeMillis()
        if (deadline != null) {
            var timeLeft = deadline - curTime
            while (timeLeft > 0) {
                // emit the current time left so we can display it
                emit(timeLeft / 1000)
                timeLeft = deadline - System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
            // emit negative value to prompt fragment to stop counting
            emit(-1)
        }
    }

    fun verifyOTP(phoneNumber: String, otp: Int): LiveData<OTPVerification> = liveData(Dispatchers.IO) {
        val result = userRepository.verifyOTP(phoneNumber, otp)
        // if the sign in is successful, sign in with the token
        if (result.isSuccessful) {
            firebaseAuth.signInWithCustomToken(result.token).await()
        }
        emit(result)
    }

}