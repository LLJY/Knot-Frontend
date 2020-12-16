package com.lucas.knot

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import services.UserOuterClass

fun CharSequence.isValidEmail() = !isNullOrEmpty() && Patterns.EMAIL_ADDRESS.matcher(this).matches()
fun CharSequence.isValidPhoneNumber() = !isNullOrEmpty() && Patterns.PHONE.matcher(this).matches()

/**
 * Converts the protobuf message into the app equivalent
 */


fun UserOuterClass.GetUserInfoResponse.mapToAppModel() = UserInfo(
        userid,
        phoneNumber,
        userName,
        bio,
        isExists,
        profilePictureUri
)

fun Users.mapToAppModel() = UserInfo(
        id,
        phone_number,
        user_name,
        bio ?: "",
        isExists == 1L,
        profilePictureURL ?: ""
)

fun Medias.mapToAppModel() = Media(
        mime_type,
        media_url,
        size
)
// observing livedata once

fun <T> LiveData<T>.observeForeverOnce(observer: Observer<T>) {
    observeForever(object : Observer<T> {
        override fun onChanged(t: T?) {
            observer.onChanged(t)
            removeObserver(this)
        }
    })
}