package com.newgrokchat.ui.settings

import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.newgrokchat.NewGrokChatApp
import com.newgrokchat.data.local.ApiConfig
import com.newgrokchat.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { NewGrokChatApp.instance.prefs }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupEndpointSelector()
        loadSettings()
        setupSaveButton()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Settings"
        }
    }
    
    private fun setupEndpointSelector() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            ApiConfig.ENDPOINTS
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerEndpoint.adapter = adapter
    }
    
    private fun loadSettings() {
        binding.editApiKey.setText(prefs.apiKey)
        
        val endpointIndex = ApiConfig.ENDPOINTS.indexOf(prefs.selectedEndpoint)
        if (endpointIndex >= 0) {
            binding.spinnerEndpoint.setSelection(endpointIndex)
        }
    }
    
    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val apiKey = binding.editApiKey.text.toString().trim()
            val endpoint = binding.spinnerEndpoint.selectedItem as String
            
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "API Key cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            prefs.apiKey = apiKey
            prefs.selectedEndpoint = endpoint
            
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
