package com.lucas.knot

import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.DiffUtil
import java.io.Serializable

data class Chat(var userInfo: LiveData<UserInfo>?, var groupId: String?, var chatTitle: String?, var messages: MutableList<Message>, var groupImageUrl: String?, var members: List<LiveData<UserInfo>>?) : Serializable

class ChatDiffutil : DiffUtil.ItemCallback<Chat>() {
    override fun areItemsTheSame(oldItem: Chat, newItem: Chat): Boolean {
        return if (oldItem.groupId != null) {
            oldItem.groupId == newItem.groupId
        } else {
            oldItem.userInfo == newItem.userInfo
        }
    }

    override fun areContentsTheSame(oldItem: Chat, newItem: Chat): Boolean {
        return oldItem.hashCode() == newItem.hashCode()
    }

}

data class Message(var id: String, var replyId: String?, var media: Media?, var isForward: Boolean, var messageStatus: MessageStatus, var datePosted: Long, var message: String, var receiverUser: LiveData<UserInfo>?, var senderUser: LiveData<UserInfo>)

object MessageDiffUtil : DiffUtil.ItemCallback<Message>() {
    override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
        return oldItem.hashCode() == newItem.hashCode()
    }

}

enum class MessageStatus {
    SENT,
    RECEIVED,
    READ
}

data class Media(var mimeType: String, var mediaUrl: String, var sizeBytes: Long)

data class UserInfo(var userId: String, var phoneNumber: String, var userName: String, var bio: String, var isExists: Boolean, var profilePictureUrl: String)
