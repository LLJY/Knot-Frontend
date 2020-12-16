package com.lucas.knot

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.lucas.knot.databinding.ChatBubbleBinding
import com.lucas.knot.databinding.ChatDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import javax.inject.Inject

/**
 * A fragment representing a single Chat detail screen.
 * This fragment is either contained in a [ChatListActivity]
 * in two-pane mode (on tablets) or a [ChatDetailActivity]
 * on handsets.
 */
@AndroidEntryPoint
class ChatDetailFragment : Fragment() {
    private val viewModel: ChatDetailViewModel by activityViewModels()
    lateinit var adapter: ChatBubbleRecyclerAdapter

    @Inject
    lateinit var firebaseAuth: FirebaseAuth
    private lateinit var binding: ChatDetailBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = ChatDetailBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // bind all the views
        lifecycleScope.launch {
            viewModel.eventListener()
        }
        viewModel.readAllMessages()
        viewModel.chatLiveData.observe(viewLifecycleOwner) {
            lifecycleScope.launch(Dispatchers.Main) {
                // sort by dateposted so it's not a mess and remove random blank messages
                val messages = viewModel.selectedChat.messages.sortedBy { msg -> msg.datePosted }.filter { msg -> msg.message.isNotBlank() }
                adapter.submitList(viewModel.selectedChat.messages)
                adapter.notifyDataSetChanged()
                binding.recycler.scrollToPosition(viewModel.selectedChat.messages.lastIndex)
            }

        }
        if (viewModel.selectedChat.groupId != null) {
            binding.chatTitleTv.text = viewModel.selectedChat.chatTitle
        } else {
            viewModel.selectedChat.userInfo?.observe(viewLifecycleOwner) {
                binding.chatTitleTv.text = it.userName
                binding.chatImage.load(it.profilePictureUrl)
            }
        }
        binding.backButton.setOnClickListener {
            activity?.onBackPressed()
        }
        adapter = context?.let { firebaseAuth.currentUser?.uid?.let { it1 -> ChatBubbleRecyclerAdapter(this, it, it1) } }!!
        binding.recycler.layoutManager = LinearLayoutManager(context)
        binding.recycler.adapter = adapter
        adapter.submitList(viewModel.selectedChat.messages)
        binding.sendButton.setOnClickListener {
            // send a message if it is not blank
            if (!binding.messageText.text.isNullOrBlank()) {
                viewModel.sendMessage(binding.messageText.text.toString(), null).observe(viewLifecycleOwner) {
                    if (!it) {
                        Snackbar.make(binding.root, "Something went wrong when sending your message", Snackbar.LENGTH_SHORT).show()
                    }
                }
                binding.messageText.setText("")
            }
        }
    }

    class ChatBubbleRecyclerAdapter(private val lifecycleOwner: LifecycleOwner, private val context: Context, private val userId: String) : ListAdapter<Message, ChatBubbleRecyclerAdapter.ViewHolder>(MessageDiffUtil) {
        inner class ViewHolder(val binding: ChatBubbleBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ChatBubbleBinding.inflate(LayoutInflater.from(context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            item.senderUser.observe(lifecycleOwner) {
                if (it.userId == this.userId) {
                    holder.binding.sendBubble.isVisible = true
                    holder.binding.receiveBubble.isVisible = false
                    holder.binding.sendMessageText.text = item.message
                    holder.binding.sendTimeText.text = SimpleDateFormat("hh.mm aa").format(item.datePosted)
                } else {
                    holder.binding.sendBubble.isVisible = false
                    holder.binding.receiveBubble.isVisible = true
                    holder.binding.receiveMessageText.text = item.message
                    holder.binding.receiveTimeText.text = SimpleDateFormat("hh.mm aa").format(item.datePosted)
                }
            }
//            item.receiverUser?.observe(lifecycleOwner){
//                if(it.userId == this.userId){
//                    holder.binding.sendBubble.isVisible = true
//                    holder.binding.receiveBubble.isVisible = false
//                    holder.binding.sendMessageText.text = item.message
//                    holder.binding.sendTimeText.text = SimpleDateFormat("hh.mm aa").format(item.datePosted)
//                }else{
//                    holder.binding.sendBubble.isVisible = false
//                    holder.binding.receiveBubble.isVisible = true
//                    holder.binding.receiveMessageText.text = item.message
//                    holder.binding.receiveTimeText.text = SimpleDateFormat("hh.mm aa").format(item.datePosted)
//                }
//            }
        }
    }
}

