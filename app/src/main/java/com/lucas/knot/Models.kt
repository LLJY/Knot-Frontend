package com.lucas.knot

data class Chat(var userId: String, var groupId: String)

data class Message(var id: String, var replyId: String?, var media: Media?, var isForward: Boolean, var messageStatus: MessageStatus, var datePosted: Long, var message: String, var receiverUserId: String)

enum class MessageStatus {
    SENT,
    RECEIVED,
    READ
}

data class Media(var mimeType: String, var mediaUrl: String, var sizeBytes: Long)