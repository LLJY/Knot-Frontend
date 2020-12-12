package com.lucas.knot

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val chatListViewModel: ChatListViewModel by viewModels()

    @Inject
    lateinit var firebaseAuth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewModel.checkLoggedIn().observe(this) {
            // if logged in, skip to the next activity
            if (it) {
                val intent = Intent(this, ChatListActivity::class.java)
                chatListViewModel.userId = firebaseAuth.currentUser!!.uid
                startActivity(intent)
            }
        }

    }
}