package com.lucas.knot

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewModel.checkLoggedIn().observe(this) {
            // if logged in, skip to the next activity
            if (it) {
                val intent = Intent(this, ChatListActivity::class.java)
                startActivity(intent)
            }
        }

    }
}