package com.newgrokchat.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.newgrokchat.R
import com.newgrokchat.databinding.ActivityMainBinding
import com.newgrokchat.ui.chat.ChatFragment

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ChatFragment())
                .commit()
        }
    }
}
