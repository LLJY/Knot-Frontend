package com.lucas.knot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.lucas.knot.databinding.SelectUserContentBinding
import com.lucas.knot.databinding.SelectUserFragmentBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SelectUserActivity : AppCompatActivity() {

    private val viewModel: SelectUserViewModel by viewModels()
    private val binding: SelectUserFragmentBinding by lazy {
        SelectUserFragmentBinding.inflate(
            layoutInflater
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val adapter = SelectUserAdapter(this, viewModel.getAllUsers())
        binding.userSelectRecycler.layoutManager = LinearLayoutManager(this)
        binding.userSelectRecycler.adapter = adapter
        // tell the other activities that a user has been detected
        adapter.clickLiveData.observe(this) {
            // launch the activity with extras so it knows someone was selected
            startActivity(Intent(this, ChatListActivity::class.java).apply {
                putExtra("SELECTED_ID", it)
            })
        }
    }


}

class SelectUserAdapter(private val context: Context, private val data: List<LiveData<UserInfo>>) :
    RecyclerView.Adapter<SelectUserAdapter.ViewHolder>() {
    class ViewHolder(var binding: SelectUserContentBinding) : RecyclerView.ViewHolder(binding.root)

    // livedata to return the user id when clicked
    private val clickMutableLiveData: MutableLiveData<String> = MutableLiveData()

    val clickLiveData: LiveData<String> get() = clickMutableLiveData
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            SelectUserContentBinding.inflate(
                LayoutInflater.from(context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        // listen to userinfo so we display user data when it is available
        item.observeForeverOnce {
            holder.binding.chatImage.load(it.profilePictureUrl)
            holder.binding.chatNameTv.text = it.userName
            // post the userid if clicked
            holder.binding.root.setOnClickListener { v ->
                clickMutableLiveData.postValue(it.userId)
            }
        }
    }

    override fun getItemCount(): Int {
        return data.count()
    }
}