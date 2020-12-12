package com.lucas.knot

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import services.ChatGrpc
import services.ChatOuterClass
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(private val chatStub: ChatGrpc.ChatBlockingStub, private val chatAsyncStub: ChatGrpc.ChatStub, private val userRepository: UserRepository, private val androidDatabase: Database) {
    var chatEventStream: StreamObserver<ChatOuterClass.Event>? = null
    var newMessageMutableLiveData: MutableLiveData<Pair<String, Message>> = MutableLiveData()

    val newMessagesLiveData: LiveData<Pair<String, Message>> get() = newMessageMutableLiveData
    suspend fun getAllChats(userId: String): MutableList<Chat> {
        // clear the database when getting all chats
        withContext(Dispatchers.IO) {
            androidDatabase.usersInGroupChatsQueries.deleteAllFields()
            androidDatabase.groupChatsQueries.deleteAllFields()
            androidDatabase.chatsQueries.deleteAllFields()
            androidDatabase.messagesQueries.deleteAllFields()
        }
        val result = withContext(Dispatchers.Default) {
            val request = ChatOuterClass.AllMessagesRequest.newBuilder()
                    .setUserid(userId)
                    .build()
            chatStub.getAllMessages(request)
        }
        val messages = result.messageList
        // partition the lists so we have both group messages and peer 2 peer messages
        // much more efficient as we need not filter twice on the same list
        val partitionedMessages = messages.partition { it.groupId.isNotBlank() }
        // group by group id to get all messages of each group
        val groupMessages = partitionedMessages.first.groupBy { it.groupId }
        // run long running operation in a coroutine
        val groupChats = withContext(Dispatchers.IO) {
            groupMessages.map { groupedMessages ->
                val groupRequest = ChatOuterClass.GetGroupsRequest.newBuilder()
                        .setGroupId(groupedMessages.key)
                        .build()
                val response = chatStub.getGroupInfo(groupRequest)
                val groupMemberInfos = response.groupMemberIdsList.map { userRepository.getUserInfo(it) }
                if (response.groupImage != null) {
                    // insert the media, then get the id and insert the group
                    androidDatabase.mediaQueries.insertOrReplaceMedia(response.groupImage.mediaUrl, response.groupImage.mimeType, response.groupImage.sizeBytes, response.groupImage.mediaUrl, null)
                    val mediaId = androidDatabase.mediaQueries.lastInsertedRowId().executeAsOne()
                    androidDatabase.groupChatsQueries.insertOrReplace(groupedMessages.key, response.title, mediaId)
                } else {
                    // if media is null set group photo to null
                    androidDatabase.groupChatsQueries.insertOrReplace(groupedMessages.key, response.title, null)
                }
                // add group to list of chats
                androidDatabase.chatsQueries.insertOrReplace(null, groupedMessages.key)
                // insert all members into the list
                response.groupMemberIdsList.forEach {
                    androidDatabase.usersInGroupChatsQueries.insertGroupMemberAndGroupId(groupedMessages.key, it)
                }
                Chat(null, response.groupId, response.title, groupedMessages.value.map { it.mapToAppModel() }.toMutableList(), response.groupImage.mediaUrl, groupMemberInfos)
            }
        }
        // distinguish between messages sent by user and received by user
        val userSentAndUserReceived = partitionedMessages.second.partition { it.senderInfo.userid == userId }
        // group by messages sent and received
        val userSentMessages = userSentAndUserReceived.first.groupBy { it.receiverUserId }
        val userReceivedMessages = userSentAndUserReceived.second.groupBy { it.senderInfo.userid }.toMutableMap()
        // run long running operation in a coroutine
        val peerChats = withContext(Dispatchers.IO) {
            // equivalent of left outer join
            userSentMessages.map {
                val allMessages = it.value.toMutableList()
                val receivedMessages = userReceivedMessages[it.key]
                if (receivedMessages != null) {
                    allMessages.addAll(receivedMessages)
                }
                // remove the object with the key, so we can add the rest of the messages to the list for
                // a full join equivalent later.
                userReceivedMessages.remove(it.key)
                // get user info will insert user information into the db, so we can directly add it in
                val userInfoResponse = userRepository.getUserInfo(it.value[0].receiverUserId)
                androidDatabase.chatsQueries.insertOrReplace(it.key, null)
                val chatId = androidDatabase.chatsQueries.lastInsertedRowId().executeAsOne()
                // add all the messages to database
                it.value.forEach { message ->
                    // if it is a media message, add media to database and add a foreign key, otherwise, set the relationship to null
                    if (message.media != null) {
                        androidDatabase.mediaQueries.insertOrReplaceMedia(message.media.mediaUrl, message.media.mimeType, message.media.sizeBytes, message.media.mediaUrl, null)
                        val latestId = androidDatabase.mediaQueries.lastInsertedRowId().executeAsOne()
                        androidDatabase.messagesQueries.insertOrReplaceMessage(message.id, latestId, message.message, message.replyId, if (message.isForward) 1 else 0, message.senderInfo.userid, message.groupId, message.datePostedUnixTimestamp, message.receiverUserId, chatId)
                    } else {
                        androidDatabase.messagesQueries.insertOrReplaceMessage(message.id, null, message.message, message.replyId, if (message.isForward) 1 else 0, message.senderInfo.userid, message.groupId, message.datePostedUnixTimestamp, message.receiverUserId, chatId)
                    }
                }
                Chat(userInfoResponse, null, null, allMessages.map { it.mapToAppModel() } as MutableList<Message>, null, null)
            }
        }
        // at this point userReceived should have a few remaining messages
        val remainingChats = userReceivedMessages.map {
            val userInfoResponse = userRepository.getUserInfo(it.key)
            androidDatabase.chatsQueries.insertOrReplace(it.key, null)
            val chatId = androidDatabase.chatsQueries.lastInsertedRowId().executeAsOne()
            // add all the messages to local database
            it.value.forEach { message ->
                // if it is a media message, add media to database and add a foreign key, otherwise, set the relationship to null
                if (message.media != null) {
                    androidDatabase.mediaQueries.insertOrReplaceMedia(message.media.mediaUrl, message.media.mimeType, message.media.sizeBytes, message.media.mediaUrl, null)
                    val latestId = androidDatabase.mediaQueries.lastInsertedRowId().executeAsOne()
                    androidDatabase.messagesQueries.insertOrReplaceMessage(message.id, latestId, message.message, message.replyId, if (message.isForward) 1 else 0, message.senderInfo.userid, message.groupId, message.datePostedUnixTimestamp, message.receiverUserId, chatId)
                } else {
                    androidDatabase.messagesQueries.insertOrReplaceMessage(message.id, null, message.message, message.replyId, if (message.isForward) 1 else 0, message.senderInfo.userid, message.groupId, message.datePostedUnixTimestamp, message.receiverUserId, chatId)
                }
            }
            Chat(userInfoResponse, null, null, it.value.map { it.mapToAppModel() } as MutableList<Message>, null, null)
        }
        // add all the chats into a list
        val allChats = groupChats.toMutableList()
        allChats.addAll(peerChats)
        allChats.addAll(remainingChats)
        Log.e("All fields", androidDatabase.chatsQueries.getAll().executeAsList().toString())
        return allChats
    }

    suspend fun eventStream(userId: String) {
        val requestObserver = chatAsyncStub.eventStream(object : StreamObserver<ChatOuterClass.Event> {
            override fun onNext(value: ChatOuterClass.Event?) {
                //TODO IMPLEMENT THE OTHER STREAMS
                if (value?.message != null) {
                    // insert the new message into local database then post the value to livedata so view can update
                    if (value.message.media != null) {
                        val chatId = androidDatabase.chatsQueries.getChatIdByUserId(value.senderInfo.userid).executeAsOne()
                        androidDatabase.mediaQueries.insertOrReplaceMedia(value.message.media.mediaUrl, value.message.media.mimeType, value.message.media.sizeBytes, value.message.media.mediaUrl, null)
                        val latestId = androidDatabase.mediaQueries.lastInsertedRowId().executeAsOne()
                        androidDatabase.messagesQueries.insertOrReplaceMessage(value.message.id, latestId, value.message.message, if (value.message.replyId.isNotBlank()) value.message.replyId else null, if (value.message.isForward) 1 else 0, value.senderInfo.userid, if (value.message.groupId.isNotBlank()) value.message.groupId else null, value.message.datePostedUnixTimestamp, if (value.message.receiverUserId.isNotBlank()) value.message.receiverUserId else null, chatId)
                        val media = Media(value.message.media.mimeType, value.message.media.mediaUrl, value.message.media.sizeBytes)
                        newMessageMutableLiveData.postValue(Pair(chatId.toString(), Message(value.message.id, if (value.message.replyId.isNotBlank()) value.message.replyId else null, media, value.message.isForward, MessageStatus.values()[value.message.messageStatus], value.message.datePostedUnixTimestamp, value.message.message, if (value.message.receiverUserId.isNotBlank()) userRepository.getUserInfo(value.message.receiverUserId) else null, userRepository.getUserInfo(value.senderInfo.userid))))
                    } else {
                        val chatId = androidDatabase.chatsQueries.getChatIdByUserId(value.senderInfo.userid).executeAsOne()
                        androidDatabase.messagesQueries.insertOrReplaceMessage(value.message.id, null, value.message.message, if (value.message.replyId.isNotBlank()) value.message.replyId else null, if (value.message.isForward) 1 else 0, value.senderInfo.userid, if (value.message.groupId.isNotBlank()) value.message.groupId else null, value.message.datePostedUnixTimestamp, if (value.message.receiverUserId.isNotBlank()) value.message.receiverUserId else null, chatId)
                        newMessageMutableLiveData.postValue(Pair(chatId.toString(), Message(value.message.id, if (value.message.replyId.isNotBlank()) value.message.replyId else null, null, value.message.isForward, MessageStatus.values()[value.message.messageStatus], value.message.datePostedUnixTimestamp, value.message.message, if (value.message.receiverUserId.isNotBlank()) userRepository.getUserInfo(value.message.receiverUserId) else null, userRepository.getUserInfo(value.senderInfo.userid))))
                    }
                }
            }

            override fun onError(t: Throwable?) {
                TODO("Not yet implemented")
            }

            override fun onCompleted() {
                TODO("Not yet implemented")
            }

        })
        chatEventStream = requestObserver
    }

    fun ChatOuterClass.Message.mapToAppModel() = Message(
            this.id,
            this.replyId,
            if (media != null) Media(
                    this.media.mimeType,
                    this.media.mediaUrl,
                    this.media.sizeBytes
            ) else null,
            this.isForward,
            MessageStatus.values()[this.messageStatus],
            this.datePostedUnixTimestamp,
            this.message,
            userRepository.getUserInfo(receiverUserId),
            userRepository.getUserInfo(senderInfo.userid)
    )
}