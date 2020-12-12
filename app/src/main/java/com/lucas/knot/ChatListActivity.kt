package com.lucas.knot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.lucas.knot.databinding.ActivityChatListBinding
import com.lucas.knot.databinding.ChatListContentBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat

/**
 * An activity representing a list of Pings. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a [ChatDetailActivity] representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
@AndroidEntryPoint
class ChatListActivity : AppCompatActivity() {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var twoPane: Boolean = false
    private val binding: ActivityChatListBinding by lazy { ActivityChatListBinding.inflate(layoutInflater) }
    private lateinit var adapter: ChatListRecyclerAdapter
    private val viewModel: ChatListViewModel by viewModels()
    private val chatDetailViewModel: ChatDetailViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        toolbar.title = title
        binding.fab.setOnClickListener {
            Snackbar.make(it, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
        if (binding.chatListIncludeLayout.chatDetailContainer != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            twoPane = true
        }
        setupRecyclerView(binding.chatListIncludeLayout.chatList)
        viewModel.getChats().observe(this) {
            adapter.submitList(it)
        }

    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        adapter = ChatListRecyclerAdapter(this, this)
        recyclerView.adapter = adapter
        adapter.onItemClickLiveData.observe(this) {
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

    class ChatListRecyclerAdapter(var context: Context, var lifecycleOwner: LifecycleOwner) : ListAdapter<Chat, ChatListRecyclerAdapter.ViewHolder>(ChatDiffutil()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ChatListContentBinding.inflate(LayoutInflater.from(context), parent, false))
        }

        private val mutableOnClickListenerLiveData: MutableLiveData<Chat> = MutableLiveData()
        val onItemClickLiveData: LiveData<Chat> get() = mutableOnClickListenerLiveData

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            val lastMessage = item.messages.last()
            if (item.groupId != null) {
                holder.binding.chatImage.load(item.groupImageUrl)
                holder.binding.chatNameTv.text = item.chatTitle
                holder.binding.latestMessageDateTv.text = SimpleDateFormat("dd/MM/yyyy").format(lastMessage.datePosted)
            } else {
                item.userInfo?.observe(lifecycleOwner) {
                    holder.binding.chatNameTv.text = it.userName
                    holder.binding.chatImage.load(it.profilePictureUrl)
                }
            }
            // TODO WARNING MEMORY LEAK, PLEASE IMPLEMENT ON DISPOSE
            lastMessage.senderUser.observe(lifecycleOwner) {
                holder.binding.latestMessagePreviewTv.text = "${it.userName}: ${lastMessage.message}"
            }
            val unreadCount = item.messages.count { it.messageStatus != MessageStatus.READ }
            if (unreadCount > 0) {
                holder.binding.newMessageCounter.text = unreadCount.toString()
            } else {
                holder.binding.newMessageCounter.isVisible = false
            }
            // let the activity handle everything, we just post a livedata
            holder.binding.root.setOnClickListener {
                mutableOnClickListenerLiveData.postValue(item)
            }
        }

        // we can access the binding class directly from viewBinding object
        inner class ViewHolder(val binding: ChatListContentBinding) : RecyclerView.ViewHolder(binding.root)

    }
}