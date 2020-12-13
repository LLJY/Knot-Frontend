package com.lucas.knot

import android.util.Patterns
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