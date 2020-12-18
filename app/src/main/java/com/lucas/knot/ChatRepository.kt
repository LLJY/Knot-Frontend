package com.lucas.knot

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import services.ChatGrpc
import services.ChatOuterClass
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(private val chatStub: ChatGrpc.ChatBlockingStub, private val chatAsyncStub: ChatGrpc.ChatStub, private val userRepository: UserRepository, private val androidDatabase: Database, private val firebaseAuth: FirebaseAuth) {
    var chatEventStream: StreamObserver<ChatOuterClass.Event>? = null
    var newMessageBroadcastChannel: BroadcastChannel<Pair<Long, Message>> = BroadcastChannel(100)

    val newMessagesFlow: Flow<Pair<Long, Message>> get() = newMessageBroadcastChannel.asFlow()
    suspend fun getAllChats(userId: String): List<Chat> {
        // clear the database when getting all chats
        withContext(Dispatchers.IO) {
            androidDatabase.usersInGroupChatsQueries.deleteAllFields()
            androidDatabase.groupChatsQueries.deleteAllFields()
            androidDatabase.chatsQueries.deleteAllFields()
            androidDatabase.messagesQueries.deleteAllFields()
        }
        val result = withContext(Dispatchers.IO) {
            val request = ChatOuterClass.AllMessagesRequest.newBuilder()
                    .setUserid(userId)
                    .build()
            chatStub.getAllMessages(request)
        }
        return convertAndStoreMessages(result.messageList, userId)
    }

    suspend fun readMessage(id: String) = withContext(Dispatchers.Default) {
        // sometimes grpc likes to fuck you over and return an error
        try {
            if (id.isNotBlank()) {
                Log.e("read message", id)
                val message = ChatOuterClass.Event.newBuilder()
                        .setMessageRead(
                                ChatOuterClass.MessageRead.newBuilder()
                                        .setMessageId(id)
                                        .setMessageStatus(2)
                                        .build()
                        )
                        .setSenderInfo(
                                ChatOuterClass.SenderInfo.newBuilder()
                                        .setUserid(firebaseAuth.currentUser!!.uid)
                                        .setIsInit(false)
                                        .build()
                        )
                        .build()
                chatEventStream!!.onNext(message)
                androidDatabase.messagesQueries.readMessage(id)
            } else {

            }
        } catch (ex: Exception) {
            Log.e("readMessage ", ex.toString())
            // restart the stream
            eventStream(firebaseAuth.currentUser!!.uid)
        }
    }

    // send normal message
    suspend fun sendMessage(message: Message, authorId: String, receiverId: String?, groupId: String?) = withContext(Dispatchers.IO) {
        var chatId = 0L
        if (groupId == null) {
            val senderInfo = ChatOuterClass.SenderInfo.newBuilder()
                .setUserid(authorId)
                .setIsInit(false).build()
            val messageEvent = ChatOuterClass.Event.newBuilder()
                .setMessage(
                    ChatOuterClass.Message.newBuilder()
                        .setId(message.id)
                        .setDatePostedUnixTimestamp(System.currentTimeMillis())
                        .setReceiverUserId(receiverId)
                        .setMessage(message.message)
                        .setSenderInfo(senderInfo).build()
                )
                .setSenderInfo(senderInfo).build()
            chatId = try {
                androidDatabase.chatsQueries.getChatIdByUserId(receiverId).executeAsOne()
            } catch (ex: Exception) {
                // insert chat if not exists, usually happens with new chats
                androidDatabase.chatsQueries.insertOrReplace(receiverId, null)
                androidDatabase.chatsQueries.lastInsertedRowId().executeAsOne()
            }
            chatEventStream!!.onNext(messageEvent)
        } else {
            chatId = androidDatabase.chatsQueries.getChatIdByGroupId(groupId).executeAsOne()
        }
        androidDatabase.messagesQueries.insertOrReplaceMessage(message.id, null, message.message, message.replyId, if (message.isForward) 1 else 0, authorId, groupId, System.currentTimeMillis(), receiverId, chatId, 0L)
        chatId
    }

    suspend fun getNewChats(userId: String) = withContext(Dispatchers.IO) {
        val result = withContext(Dispatchers.IO) {
            val request = ChatOuterClass.NewMessagesRequest.newBuilder()
                    .setUserid(userId)
                    .build()
            chatStub.getUnreadMessages(request)
        }
        convertAndStoreMessages(result.messageList, userId)
    }

    /**
     * Gets all the messages from database and appends it with new messages
     * new messages will be stored in db.
     */
    suspend fun getAllMessagesWithDb(userId: String) = withContext(Dispatchers.IO) {
        val allDbMessages = androidDatabase.messagesQueries.getAllMessages().executeAsList()
        val allDbChats = androidDatabase.chatsQueries.getAll().executeAsList()
        val allChats = allDbChats.map {
            if (it.user_id_fk != null) {
                Chat(
                        it.id,
                        userRepository.getUserInfo(it.user_id_fk),
                        null,
                        null,
                        allDbMessages.filter { messages -> messages.chat_id_fk == it.id }.map { messages -> messages.mapToAppModel() } as MutableList<Message>,
                        null,
                        null
                )
            } else {
                // if user id is null, group id will not be null
                val groupInfo = androidDatabase.groupChatsQueries.getGroupById(it.group_id_fk!!).executeAsOne()
                Chat(
                        it.id,
                        null,
                        it.group_id_fk,
                        groupInfo.title,
                        allDbMessages.filter { messages -> messages.chat_id_fk == it.id }.map { messages -> messages.mapToAppModel() } as MutableList<Message>,
                        androidDatabase.mediaQueries.getMediaById(it.group_photo_fk!!).executeAsOne().media_url,
                        androidDatabase.usersInGroupChatsQueries.getGroupMembersByGroupId(it.group_id_fk).executeAsList().map { userid -> userRepository.getUserInfo(userid) }
                )
            }
        } as MutableList
        // get all chats for now, new chats still needs some work
        try {
            val newChats = getNewChats(userId).toMutableList()
            newChats.forEach {
                val chat = allChats.firstOrNull { chat -> chat.id == it.id }
                if (chat != null) {
                    val chatIndex = allChats.indexOf(chat)
                    allChats[chatIndex].messages.addAll(it.messages)
                    allChats[chatIndex].messages = allChats[chatIndex].messages.distinctBy { message -> message.id } as MutableList<Message>
                }
            }
            allChats
        } catch (ex: Exception) {
            // do nothing if no internet connection.
            allChats
        }

    }

    private suspend fun convertAndStoreMessages(messages: List<ChatOuterClass.Message>, userId: String): List<Chat> = withContext(Dispatchers.IO) {
        // partition the lists so we have both group messages and peer 2 peer messages
        // much more efficient as we need not filter twice on the same list
        val msg = messages.sortedBy { it.datePostedUnixTimestamp }
        val partitionedMessages = msg.partition { it.groupId.isNotBlank() }
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
                Chat(androidDatabase.chatsQueries.lastInsertedRowId().executeAsOne(), null, response.groupId, response.title, groupedMessages.value.map { it.mapToAppModel() }.toMutableList(), response.groupImage.mediaUrl, groupMemberInfos)
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
                // get user info will insert user information into the db, so we can directly add it in
                val userInfoResponse = userRepository.getUserInfo(it.value[0].receiverUserId)
                var chatId = androidDatabase.chatsQueries.getChatIdByUserId(it.key).executeAsOneOrNull()
                if (chatId == null) {
                    androidDatabase.chatsQueries.insertOrReplace(it.key, null)
                    chatId = androidDatabase.chatsQueries.lastInsertedRowId().executeAsOne()
                }
                // add all the messages to database
                it.value.forEach { message ->
                    // if it is a media message, add media to database and add a foreign key, otherwise, set the relationship to null
                    if (message.media != null) {
                        androidDatabase.mediaQueries.insertOrReplaceMedia(message.media.mediaUrl, message.media.mimeType, message.media.sizeBytes, message.media.mediaUrl, null)
                        val latestId = androidDatabase.mediaQueries.lastInsertedRowId().executeAsOne()
                        androidDatabase.messagesQueries.insertOrReplaceMessage(message.id, latestId, message.message, message.replyId, if (message.isForward) 1 else 0, message.senderInfo.userid, message.groupId, message.datePostedUnixTimestamp, message.receiverUserId, chatId, message.messageStatus.toLong())
                    } else {
                        androidDatabase.messagesQueries.insertOrReplaceMessage(message.id, null, message.message, message.replyId, if (message.isForward) 1 else 0, message.senderInfo.userid, message.groupId, message.datePostedUnixTimestamp, message.receiverUserId, chatId, message.messageStatus.toLong())
                    }
                }
                Chat(chatId, userInfoResponse, null, null, allMessages.map { it.mapToAppModel() } as MutableList<Message>, null, null)
            }
        }
        // at this point userReceived should have a few remaining messages
        val remainingChats = userReceivedMessages.map {
            val userInfoResponse = userRepository.getUserInfo(it.key)
            var chatId = androidDatabase.chatsQueries.getChatIdByUserId(it.key).executeAsOneOrNull()
            if (chatId == null) {
                androidDatabase.chatsQueries.insertOrReplace(it.key, null)
                chatId = androidDatabase.chatsQueries.lastInsertedRowId().executeAsOne()
            }
            // add all the messages to local database
            it.value.forEach { message ->
                // if it is a media message, add media to database and add a foreign key, otherwise, set the relationship to null
                if (message.media != null) {
                    androidDatabase.mediaQueries.insertOrReplaceMedia(message.media.mediaUrl, message.media.mimeType, message.media.sizeBytes, message.media.mediaUrl, null)
                    val latestId = androidDatabase.mediaQueries.lastInsertedRowId().executeAsOne()
                    androidDatabase.messagesQueries.insertOrReplaceMessage(message.id, latestId, message.message, message.replyId, if (message.isForward) 1 else 0, message.senderInfo.userid, message.groupId, message.datePostedUnixTimestamp, message.receiverUserId, chatId, message.messageStatus.toLong())
                } else {
                    androidDatabase.messagesQueries.insertOrReplaceMessage(message.id, null, message.message, message.replyId, if (message.isForward) 1 else 0, message.senderInfo.userid, message.groupId, message.datePostedUnixTimestamp, message.receiverUserId, chatId, message.messageStatus.toLong())
                }
            }
            Chat(chatId!!, userInfoResponse, null, null, it.value.map { it.mapToAppModel() } as MutableList<Message>, null, null)
        }
        groupChats.forEach {
            it.messages = it.messages.sortedBy { msg -> msg.datePosted }.toMutableList()
        }
        // add all the chats into a list
        val allChats = groupChats.toMutableList()
        allChats.addAll(peerChats)
        allChats.addAll(remainingChats)
        allChats
    }

    suspend fun eventStream(userId: String) {
        val requestObserver = chatAsyncStub.eventStream(object : StreamObserver<ChatOuterClass.Event> {
            override fun onNext(value: ChatOuterClass.Event?) {
                //TODO IMPLEMENT THE OTHER STREAMS
                if (value?.hasMessage() == true) {
                    // make sure the message is valid
                    // insert the new message into local database then post the value to livedata so view can update
                    if (value.message.media != null) {
                        val chatId = androidDatabase.chatsQueries.getChatIdByUserId(value.senderInfo.userid).executeAsOne()
                        androidDatabase.mediaQueries.insertOrReplaceMedia(value.message.media.mediaUrl, value.message.media.mimeType, value.message.media.sizeBytes, value.message.media.mediaUrl, null)
                        val latestId = androidDatabase.mediaQueries.lastInsertedRowId().executeAsOne()
                        androidDatabase.messagesQueries.insertOrReplaceMessage(value.message.id, latestId, value.message.message, if (value.message.replyId.isNotBlank()) value.message.replyId else null, if (value.message.isForward) 1 else 0, value.senderInfo.userid, if (value.message.groupId.isNotBlank()) value.message.groupId else null, value.message.datePostedUnixTimestamp, if (value.message.receiverUserId.isNotBlank()) value.message.receiverUserId else null, chatId, value.message.messageStatus.toLong())
                        val media = Media(value.message.media.mimeType, value.message.media.mediaUrl, value.message.media.sizeBytes)
                        GlobalScope.launch {
                            newMessageBroadcastChannel.send(Pair(chatId, Message(value.message.id, if (value.message.replyId.isNotBlank()) value.message.replyId else null, media, value.message.isForward, MessageStatus.values()[value.message.messageStatus], value.message.datePostedUnixTimestamp, value.message.message, if (value.message.receiverUserId.isNotBlank()) userRepository.getUserInfo(value.message.receiverUserId) else null, userRepository.getUserInfo(value.senderInfo.userid))))
                        }
                    } else {
                        val chatId = androidDatabase.chatsQueries.getChatIdByUserId(value.senderInfo.userid).executeAsOne()
                        androidDatabase.messagesQueries.insertOrReplaceMessage(value.message.id, null, value.message.message, if (value.message.replyId.isNotBlank()) value.message.replyId else null, if (value.message.isForward) 1 else 0, value.senderInfo.userid, if (value.message.groupId.isNotBlank()) value.message.groupId else null, value.message.datePostedUnixTimestamp, if (value.message.receiverUserId.isNotBlank()) value.message.receiverUserId else null, chatId, value.message.messageStatus.toLong())
                        GlobalScope.launch {
                            newMessageBroadcastChannel.send(Pair(chatId, Message(value.message.id, if (value.message.replyId.isNotBlank()) value.message.replyId else null, null, value.message.isForward, MessageStatus.values()[value.message.messageStatus], value.message.datePostedUnixTimestamp, value.message.message, if (value.message.receiverUserId.isNotBlank()) userRepository.getUserInfo(value.message.receiverUserId) else null, userRepository.getUserInfo(value.senderInfo.userid))))
                        }
                    }
                }
            }

            override fun onError(t: Throwable?) {
                // restart the event stream every time something goes wrong
                // cos hax and fuck grpc
                Log.e("grpc error", t.toString())
                GlobalScope.launch {
                    eventStream(userId)
                }
            }

            override fun onCompleted() {

            }

        })
        // send the initial event to let them know we are online
        val initialEvent = ChatOuterClass.Event.newBuilder()
                .setSenderInfo(ChatOuterClass.SenderInfo.newBuilder().setIsInit(true).setUserid(userId).build())
                .build()
        requestObserver.onNext(initialEvent)
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

    suspend fun Messages.mapToAppModel() = withContext(Dispatchers.IO) {
        Message(
                id,
                reply_message_id_fk,
                if (media_id_fk != null) androidDatabase.mediaQueries.getMediaById(media_id_fk).executeAsOne().mapToAppModel() else null,
                is_forward == 1L,
                MessageStatus.values()[message_status.toInt()],
                date_posted,
                message,
                if (reciever_user_id != null) userRepository.getUserInfo(reciever_user_id) else null,
                userRepository.getUserInfo(author_id_fk)
        )
    }
}