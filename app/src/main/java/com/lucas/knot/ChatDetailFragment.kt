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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.lucas.knot.databinding.ChatBubbleBinding
import com.lucas.knot.databinding.ChatDetailBinding
import dagger.hilt.android.AndroidEntryPoint
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

