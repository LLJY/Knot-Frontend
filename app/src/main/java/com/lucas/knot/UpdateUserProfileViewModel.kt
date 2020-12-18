package com.lucas.knot

import android.net.Uri
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import java.util.*

class UpdateUserProfileViewModel @ViewModelInject constructor(
    private val userRepository: UserRepository,
    private val storage: FirebaseStorage
) : ViewModel() {
    var username: String = ""
    var bio: String = ""
    var fileUrl: String = ""
    fun uploadFile(filePath: Uri) = liveData(Dispatchers.IO) {
        // attempt to upload the file using firebase storage
        emit(
            try {
                val ref = storage.reference
                val storageRef = ref.child("images/${UUID.randomUUID()}")
                val uploadTask = storageRef.putFile(filePath).await()
                fileUrl = storageRef.downloadUrl.await().toString()
            } catch (ex: Exception) {
                ""
            }
        )
    }

    /**
     * updates the user profile
     * @param phoneNumber we need phone number from the previous fragments, get it from MainViewModel
     */
    fun updateProfile(phoneNumber: String) = liveData(Dispatchers.IO) {
        emit(userRepository.updateUserInfo(username, bio, fileUrl, phoneNumber))
    }
}