package com.lucas.knot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.lucas.knot.databinding.ActivityChatListBinding
import com.lucas.knot.databinding.ChatListContentBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import javax.inject.Inject


@AndroidEntryPoint
class ChatListActivity : AppCompatActivity() {
    private val MY_PERMISSIONS_REQUEST_CAMERA = 100
    private val MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 101
    private val MY_PERMISSIONS_REQUEST = 102

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var twoPane: Boolean = false
    private val binding: ActivityChatListBinding by lazy {
        ActivityChatListBinding.inflate(
            layoutInflater
        )
    }
    private lateinit var adapter: ChatListRecyclerAdapter
    private val viewModel: ChatListViewModel by viewModels()
    private val chatDetailViewModel: ChatDetailViewModel by viewModels()

    @Inject
    lateinit var firebaseAuth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        Log.e("oncreate", "aaaaaaaaa")
        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        toolbar.title = title
        binding.fab.setOnClickListener {
            startActivity(Intent(this, SelectUserActivity::class.java))
        }
        viewModel.getAdvert().observe(this) {
            binding.chatListIncludeLayout.advertImage.load(it.imageUrl)
            binding.chatListIncludeLayout.advertTitle.text = it.title
            binding.chatListIncludeLayout.advertText.text = it.message
        }
        val selectedUser = intent.getStringExtra("SELECTED_ID")
        if (selectedUser != null) {
            // this activity was launched by SelectUserActivity to launch a new chat
            selectedChat = Chat(
                -1,
                viewModel.userRepository.getUserInfo(selectedUser),
                null,
                null,
                mutableListOf(),
                null,
                null
            )
            if (twoPane) {
                chatDetailViewModel.selectedChat = selectedChat
                val fragment = ChatDetailFragment()
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.chat_detail_container, fragment)
                    .commit()
            } else {
                val intent = Intent(this, ChatDetailActivity::class.java)
                startActivity(intent)
            }
        }
        if (binding.chatListIncludeLayout.chatDetailContainer != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            twoPane = true
        }
        setupRecyclerView(binding.chatListIncludeLayout.chatList)
        viewModel.isFirstUser = intent.getBooleanExtra("IS_NEW_USER", true)
        lifecycleScope.launch {
            viewModel.getChats()
        }
        // observe the chat list and update recyclerview
        viewModel.chatLiveData.observe(this) {
            adapter.submitList(it)
            adapter.notifyDataSetChanged()
        }
        // update the token on the backend
        viewModel.updateNotificationToken().observe(this) {
            if (!it.first) {
                Snackbar.make(
                        binding.root,
                        "An error has occurred, you might not receive notifications",
                        Snackbar.LENGTH_SHORT
                ).show()
            }
        }
        // get all permissions
        askForPermissions()
        // start the event listener so we can start receiving calls
        viewModel.startSignalEventListener()

        viewModel.signalOfferListener().observe(this) {
            startActivity(Intent(this, CallActivity::class.java).apply {
                putExtra("SIGNAL_OFFER", it)
            })
        }

    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        adapter = ChatListRecyclerAdapter(this, this)
        recyclerView.adapter = adapter

        adapter.onItemClickLiveData.observe(this) {
            // if it is in tablet view, replace the fragment instead of starting a new activity
            if (twoPane) {
                chatDetailViewModel.selectedChat = it
                val fragment = ChatDetailFragment()
                supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.chat_detail_container, fragment)
                        .commit()
            } else {
                selectedChat = it
                val intent = Intent(this, ChatDetailActivity::class.java)
                startActivity(intent)
            }
        }
    }

    /**
     * Check if permissions are already granted, if not, ask for them.
     */
    private fun askForPermissions() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED)
        ) {
            ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                    MY_PERMISSIONS_REQUEST
            )
        } else if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.RECORD_AUDIO),
                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO
            )
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.CAMERA),
                    MY_PERMISSIONS_REQUEST_CAMERA
            )
        }
    }

    class ChatListRecyclerAdapter(var context: Context, var lifecycleOwner: LifecycleOwner) : ListAdapter<Chat, ChatListRecyclerAdapter.ViewHolder>(
            ChatDiffutil()
    ) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                    ChatListContentBinding.inflate(
                            LayoutInflater.from(context),
                            parent,
                            false
                    )
            )
        }

        private val mutableOnClickListenerLiveData: MutableLiveData<Chat> = MutableLiveData()
        val onItemClickLiveData: LiveData<Chat> get() = mutableOnClickListenerLiveData

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            var lastMessage: Message? = null
            try {
                lastMessage = item.messages.last()
            } catch (ex: Exception) {

            }
            // get different information based on group and stuff
            if (item.groupId != null) {
                holder.binding.chatImage.load(item.groupImageUrl)
                holder.binding.chatNameTv.text = item.chatTitle
                holder.binding.latestMessageDateTv.text = SimpleDateFormat("dd/MM/yyyy").format(
                        lastMessage?.datePosted
                )
            } else {
                item.userInfo?.observe(lifecycleOwner) {
                    holder.binding.chatNameTv.text = it.userName
                    holder.binding.chatImage.load(it.profilePictureUrl)
                }
            }
            lastMessage?.senderUser?.observe(lifecycleOwner) {
                holder.binding.latestMessagePreviewTv.text = "${it.userName}: ${lastMessage.message}"
                val unreadCount = item.messages.count { msg -> msg.messageStatus != MessageStatus.READ && it.userId != FirebaseAuth.getInstance().currentUser!!.uid }
                if (unreadCount > 0) {
                    holder.binding.newMessageCounter.text = unreadCount.toString()
                } else {
                    holder.binding.newMessageCounter.isVisible = false
                    holder.binding.cardNumberContainer.isVisible = false
                }
            }

            // let the activity handle everything, we just post a livedata
            holder.binding.root.setOnClickListener {
                mutableOnClickListenerLiveData.postValue(item)
            }
        }

        // we can access the binding class directly from viewBinding object
        inner class ViewHolder(val binding: ChatListContentBinding) : RecyclerView.ViewHolder(
                binding.root
        )

    }
}