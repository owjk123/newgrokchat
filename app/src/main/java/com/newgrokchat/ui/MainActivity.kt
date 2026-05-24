package com.newgrokchat.ui

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.newgrokchat.R
import com.newgrokchat.databinding.ActivityMainBinding
import com.newgrokchat.ui.chat.ChatFragment
import java.util.Locale

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    // Bug 6修复: TTS在Activity级别管理，避免Fragment重建导致TTS丢失
    var tts: TextToSpeech? = null
    var isTtsReady = false
        private set
    private var pendingSpeakText: String? = null
    
    // Bug 1修复: 提供共享ViewModel的工厂方法
    val sharedViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[com.newgrokchat.ui.chat.ChatViewModel::class.java]
    }
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ChatFragment())
                .commit()
        }
        
        // Bug 6修复: 在Activity中初始化TTS
        initTts()
    }
    
    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // 尝试多种中文Locale
                val locales = listOf(Locale.CHINESE, Locale.SIMPLIFIED_CHINESE, Locale.CHINA, Locale("zh", "CN"))
                var localeSet = false
                for (locale in locales) {
                    val result = tts?.setLanguage(locale)
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        localeSet = true
                        break
                    }
                }
                // 如果中文不可用，使用默认Locale
                if (!localeSet) {
                    tts?.language = Locale.getDefault()
                    Log.w(TAG, "Chinese TTS not available, using default locale")
                }
                
                isTtsReady = true
                
                // 如果有等待朗读的文本，立即朗读
                pendingSpeakText?.let {
                    speakText(it)
                    pendingSpeakText = null
                }
                
                Log.d(TAG, "TTS initialized successfully, localeSet=$localeSet")
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }
    
    /**
     * Bug 6修复: 统一的朗读方法
     */
    fun speakText(text: String) {
        if (isTtsReady && tts != null) {
            val prefs = com.newgrokchat.NewGrokChatApp.instance.prefs
            if (prefs.ttsEnabled) {
                tts?.setSpeechRate(prefs.ttsSpeed)
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
            }
        } else {
            // TTS还没准备好，缓存文本等初始化完成后朗读
            pendingSpeakText = text
        }
    }
    
    /**
     * Bug 6修复: 停止朗读
     */
    fun stopSpeaking() {
        tts?.stop()
    }
    
    /**
     * Bug 6修复: 检查TTS数据是否安装，如未安装提示用户
     */
    fun isTtsDataAvailable(): Boolean {
        return isTtsReady
    }
    
    override fun onDestroy() {
        // Bug 6修复: Activity销毁时释放TTS
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
        super.onDestroy()
    }
}
