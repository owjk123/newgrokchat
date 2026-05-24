package com.newgrokchat.ui.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.newgrokchat.NewGrokChatApp
import com.newgrokchat.R
import com.newgrokchat.data.api.GrokApiClient
import com.newgrokchat.data.local.Prefs
import com.newgrokchat.databinding.ActivitySettingsBinding
import com.newgrokchat.model.ApiConfig
import com.newgrokchat.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { NewGrokChatApp.instance.prefs }
    private val apiClient by lazy { GrokApiClient() }
    
    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Bug 4修复: 将头像图片复制到内部存储，确保持久化
                copyAvatarToInternalStorage(uri)
            }
        }
    }
    
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            openImagePicker()
        } else {
            Toast.makeText(this, "需要存储权限来选择图片", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupEndpointSelector()
        setupModelSelector()
        loadSettings()
        setupSaveButton()
        setupTestConnectionButton()
        setupAvatarSelector()
        setupTtsSettings()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "设置"
        }
    }
    
    private fun setupEndpointSelector() {
        val adapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            ApiConfig.ENDPOINTS
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerEndpoint.adapter = adapter
    }
    
    private fun setupModelSelector() {
        val adapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            ApiConfig.MODELS
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerModel.adapter = adapter
    }
    
    private fun loadSettings() {
        binding.editApiKey.setText(prefs.apiKey)
        
        val endpointIndex = ApiConfig.ENDPOINTS.indexOf(prefs.selectedEndpoint)
        if (endpointIndex >= 0) {
            binding.spinnerEndpoint.setSelection(endpointIndex)
        }
        
        val modelIndex = ApiConfig.MODELS.indexOf(prefs.selectedModel)
        if (modelIndex >= 0) {
            binding.spinnerModel.setSelection(modelIndex)
        }
        
        binding.editSystemPrompt.setText(prefs.systemPrompt)
        updateAvatarDisplay()
        
        binding.switchTts.isChecked = prefs.ttsEnabled
        binding.seekbarTtsSpeed.progress = ((prefs.ttsSpeed - 0.5f) * 10).toInt()
        binding.textTtsSpeed.text = "语速: ${String.format("%.1f", prefs.ttsSpeed)}x"
        updateTtsVisibility()
    }
    
    // Bug 4修复: 头像显示从内部存储读取
    private fun updateAvatarDisplay() {
        val avatarFile = File(filesDir, Prefs.AVATAR_FILENAME)
        if (avatarFile.exists()) {
            try {
                binding.avatarPreviewImage.visibility = View.VISIBLE
                binding.avatarPreviewText.visibility = View.GONE
                binding.avatarPreviewImage.setImageURI(Uri.fromFile(avatarFile))
                return
            } catch (e: Exception) {
                // 读取失败，fallback到默认
            }
        }
        
        val avatar = prefs.aiAvatar
        if (avatar.startsWith("http") || avatar.startsWith("content://") || avatar.startsWith("file://")) {
            try {
                binding.avatarPreviewImage.visibility = View.VISIBLE
                binding.avatarPreviewText.visibility = View.GONE
                binding.avatarPreviewImage.setImageURI(Uri.parse(avatar))
            } catch (e: Exception) {
                binding.avatarPreviewText.visibility = View.VISIBLE
                binding.avatarPreviewImage.visibility = View.GONE
                binding.avatarPreviewText.text = avatar
            }
        } else {
            binding.avatarPreviewText.visibility = View.VISIBLE
            binding.avatarPreviewImage.visibility = View.GONE
            binding.avatarPreviewText.text = if (avatar.isNotEmpty()) avatar else "🤖"
        }
    }
    
    // Bug 4修复: 将头像复制到内部存储
    private fun copyAvatarToInternalStorage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 解码并压缩
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) {
                Toast.makeText(this, "图片格式不支持", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 缩放到合理尺寸
            val maxSize = 256
            val scaledBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
                val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }
            
            // 保存到内部存储
            val avatarFile = File(filesDir, Prefs.AVATAR_FILENAME)
            val fos = FileOutputStream(avatarFile)
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.close()
            
            if (bitmap !== scaledBitmap) bitmap.recycle()
            scaledBitmap.recycle()
            
            // 更新prefs存储file路径
            prefs.aiAvatar = "file://${avatarFile.absolutePath}"
            
            updateAvatarDisplay()
            Toast.makeText(this, "头像已更新", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "头像保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupAvatarSelector() {
        binding.btnSelectAvatar.setOnClickListener {
            showAvatarOptions()
        }
    }
    
    private fun showAvatarOptions() {
        val options = arrayOf("🤖 机器人", "👽 外星人", "🧙 巫师", "🦊 狐狸", "🦁 狮子", "选择自定义图片...")
        
        AlertDialog.Builder(this)
            .setTitle("选择AI头像")
            .setItems(options) { _, which ->
                when (which) {
                    in 0..4 -> {
                        // 使用预设emoji，同时删除内部存储头像文件
                        val emojis = arrayOf("🤖", "👽", "🧙", "🦊", "🦁")
                        val avatarFile = File(filesDir, Prefs.AVATAR_FILENAME)
                        avatarFile.delete()
                        prefs.aiAvatar = emojis[which]
                        updateAvatarDisplay()
                        Toast.makeText(this, "头像已更新", Toast.LENGTH_SHORT).show()
                    }
                    5 -> {
                        checkPermissionAndPick()
                    }
                }
            }
            .show()
    }
    
    private fun checkPermissionAndPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            openImagePicker()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) 
                    == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    openImagePicker()
                }
                else -> {
                    requestPermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        } else {
            openImagePicker()
        }
    }
    
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImage.launch(intent)
    }
    
    private fun setupTtsSettings() {
        binding.switchTts.setOnCheckedChangeListener { _, isChecked ->
            prefs.ttsEnabled = isChecked
            updateTtsVisibility()
        }
        
        binding.seekbarTtsSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = 0.5f + (progress / 10f)
                binding.textTtsSpeed.text = "语速: ${String.format("%.1f", speed)}x"
                if (fromUser) {
                    prefs.ttsSpeed = speed
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun updateTtsVisibility() {
        val visible = if (binding.switchTts.isChecked) View.VISIBLE else View.GONE
        binding.textTtsSpeed.visibility = visible
        binding.seekbarTtsSpeed.visibility = visible
    }
    
    private fun setupTestConnectionButton() {
        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }
    }
    
    private fun testConnection() {
        val apiKey = binding.editApiKey.text.toString().trim()
        val endpoint = binding.spinnerEndpoint.selectedItem as String
        val model = binding.spinnerModel.selectedItem as String
        
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请先输入API密钥", Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.btnTestConnection.isEnabled = false
        binding.btnTestConnection.text = "测试中..."
        binding.testResultText.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiClient.sendMessage(
                        endpoints = listOf(endpoint),
                        apiKey = apiKey,
                        model = model,
                        messages = listOf(Message(content = "Hi", isUser = true)),
                        systemPrompt = ""
                    )
                }
                
                binding.btnTestConnection.isEnabled = true
                binding.btnTestConnection.text = "测试连接"
                binding.testResultText.visibility = View.VISIBLE
                
                result.fold(
                    onSuccess = { response ->
                        binding.testResultText.text = "✓ 连接成功！收到回复: ${response.take(50)}..."
                        binding.testResultText.setTextColor(getColor(R.color.success_green))
                    },
                    onFailure = { e ->
                        binding.testResultText.text = "✗ 连接失败: ${e.message}"
                        binding.testResultText.setTextColor(getColor(R.color.error_red))
                    }
                )
            } catch (e: Exception) {
                binding.btnTestConnection.isEnabled = true
                binding.btnTestConnection.text = "测试连接"
                binding.testResultText.visibility = View.VISIBLE
                binding.testResultText.text = "✗ 测试异常: ${e.message}"
                binding.testResultText.setTextColor(getColor(R.color.error_red))
            }
        }
    }
    
    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val apiKey = binding.editApiKey.text.toString().trim()
            val endpoint = binding.spinnerEndpoint.selectedItem as String
            val model = binding.spinnerModel.selectedItem as String
            val systemPrompt = binding.editSystemPrompt.text.toString().trim()
            
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "API密钥不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            prefs.apiKey = apiKey
            prefs.selectedEndpoint = endpoint
            prefs.selectedModel = model
            prefs.systemPrompt = systemPrompt
            
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
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
