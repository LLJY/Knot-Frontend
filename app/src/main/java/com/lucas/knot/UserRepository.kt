package com.lucas.knot

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import services.IdentityGrpc
import services.IdentityOuterClass
import services.UserGrpc
import services.UserOuterClass
import javax.inject.Inject
import javax.inject.Singleton

// if we want to return more than one value, just create a data class for it
data class OTPVerification(val token: String, val isSuccessful: Boolean, val isSignUp: Boolean)

@Singleton
class UserRepository @Inject constructor(private val identityStub: IdentityGrpc.IdentityBlockingStub, private val userStub: UserGrpc.UserBlockingStub, private val androidDatabase: Database) {

    suspend fun requestOTP(phoneNumber: String): Int = withContext(Dispatchers.IO) {
        val request = IdentityOuterClass.OTPRequest.newBuilder()
                .setPhoneNumber(phoneNumber)
                .build()
        val result = identityStub.requestOTP(request)
        result.timeLeft
    }

    suspend fun verifyOTP(phoneNumber: String, otp: Int): OTPVerification = withContext(Dispatchers.IO) {
        val request = IdentityOuterClass.VerifyOTPRequest.newBuilder()
                .setOtp(otp.toString())
                .setPhoneNumber(phoneNumber)
                .build()
        val result = identityStub.verifyOTP(request)
        OTPVerification(result.token, result.isSuccessful, result.isSignUp)
    }

    fun getUserInfo(userId: String): LiveData<UserInfo> = liveData(Dispatchers.IO) {
        val userInfo = androidDatabase.usersQueries.getUserById(userId).executeAsOneOrNull()
        // if user does not exist in database, call from api
        // TODO return live data so we can call the API anyway and provide updates later
        if (userInfo == null) {
            emit(getUserInfoFromAPI(userId))
        } else {
            emit(userInfo.mapToAppModel())
            // if database contains information, emit the API data later so it will automatically update
            emit(getUserInfoFromAPI(userId))
        }
    }

    /**
     * Gets the user information from the API and then storing it in database
     * useful for updating user information in the background
     */
    suspend fun getUserInfoFromAPI(userId: String): UserInfo = withContext(Dispatchers.IO) {
        val userInfoRequest = UserOuterClass.GetUserInfoRequest.newBuilder()
                .setUserid(userId)
                .build()
        Log.e("userid", userId)
        val response = userStub.getUserInfo(userInfoRequest)
        androidDatabase.usersQueries.insertOrReplace(userInfoRequest.userid, response.phoneNumber, response.userName, response.bio, if (response.isExists) 1 else 0, response.profilePictureUri)
        response.mapToAppModel()
    }
}