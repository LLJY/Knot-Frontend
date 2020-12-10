package com.lucas.knot

import services.IdentityGrpc
import services.IdentityOuterClass
import services.UserGrpc
import services.UserOuterClass
import javax.inject.Inject
import javax.inject.Singleton

// if we want to return more than one value, just create a data class for it
data class OTPVerification(val token: String, val isSuccessful: Boolean, val isSignUp: Boolean)

@Singleton
class UserRepository @Inject constructor(private val identityStub: IdentityGrpc.IdentityBlockingStub, private val userStub: UserGrpc.UserBlockingStub) {

    suspend fun requestOTP(phoneNumber: String): Int {
        val request = IdentityOuterClass.OTPRequest.newBuilder()
                .setPhoneNumber(phoneNumber)
                .build()
        val result = identityStub.requestOTP(request)
        return result.timeLeft
    }

    suspend fun verifyOTP(phoneNumber: String, otp: Int): OTPVerification {
        val request = IdentityOuterClass.VerifyOTPRequest.newBuilder()
                .setOtp(otp.toString())
                .setPhoneNumber(phoneNumber)
                .build()
        val result = identityStub.verifyOTP(request)
        return OTPVerification(result.token, result.isSuccessful, result.isSignUp)
    }

    suspend fun getUserInfo(userId: String) {
        val request = UserOuterClass.GetUserInfoRequest.newBuilder()
                .setUserid(userId)
                .build()
        val result = userStub.getUserInfo(request)
        TODO("return user info")
    }


}