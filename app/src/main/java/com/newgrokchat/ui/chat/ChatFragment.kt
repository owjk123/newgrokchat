package com.newgrokchat.ui.chat

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.newgrokchat.databinding.FragmentChatBinding
import com.newgrokchat.ui.settings.SettingsActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatFragment : Fragment(), TextToSpeech.OnInitListener {
    
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: ChatViewModel
    private lateinit var chatAdapter: ChatAdapter
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isScrolling = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        
        setupToolbar()
        setupModelSelector()
        setupRecyclerView()
        setupFastScroller()
        setupInputArea()
        setupNewMessageButton()
        observeViewModel()
        
        // 初始化TTS
        tts = TextToSpeech(requireContext(), this)
    }
    
    private fun setupToolbar() {
        binding.btnNewChat.setOnClickListener {
            viewModel.newConversation()
        }
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
    }
    
    private fun setupModelSelector() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            viewModel.models
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        
        binding.spinnerModel.adapter = adapter
        binding.spinnerModel.setSelection(viewModel.models.indexOf(viewModel.currentModel))
        
        binding.spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.currentModel = viewModel.models[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            onCopyClick = { message -> copyToClipboard(message.content) },
            onSpeakClick = { message -> speakMessage(message.content) },
            getAiAvatar = { viewModel.aiAvatar }
        )
        
        binding.recyclerMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            
            // 检测滚动状态
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        isScrolling = false
                        binding.scrollPreview.visibility = View.GONE
                        
                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        val lastVisible = layoutManager.findLastVisibleItemPosition()
                        val totalItems = layoutManager.itemCount
                        
                        val isAtBottom = lastVisible >= totalItems - 2
                        viewModel.setViewingHistory(!isAtBottom)
                        binding.btnScrollToBottom.visibility = if (isAtBottom) View.GONE else View.VISIBLE
                    }
                }
                
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (isScrolling) {
                        updateScrollHandle()
                    }
                }
            })
        }
    }
    
    private fun setupFastScroller() {
        binding.fastScrollerContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    isScrolling = true
                    handleFastScroll(event)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isScrolling = false
                    binding.scrollPreview.visibility = View.GONE
                    true
                }
                else -> false
            }
        }
    }
    
    private fun handleFastScroll(event: MotionEvent) {
        val container = binding.fastScrollerContainer
        val recyclerView = binding.recyclerMessages
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        
        val itemCount = chatAdapter.itemCount
        if (itemCount == 0) return
        
        // 计算触摸位置相对于容器的比例
        val containerTop = container.top.toFloat()
        val containerHeight = container.height.toFloat()
        val relativeY = event.y - containerTop
        val ratio = (relativeY / containerHeight).coerceIn(0f, 1f)
        
        // 计算目标位置
        val targetPosition = (ratio * (itemCount - 1)).toInt()
        
        // 滚动到目标位置
        layoutManager.scrollToPositionWithOffset(targetPosition, 0)
        
        // 更新滑动条位置
        updateScrollHandle()
        
        // 显示预览
        showScrollPreview(targetPosition)
    }
    
    private fun updateScrollHandle() {
        val itemCount = chatAdapter.itemCount
        if (itemCount == 0) return
        
        val layoutManager = binding.recyclerMessages.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        val currentPosition = (firstVisible + lastVisible) / 2
        
        val ratio = currentPosition.toFloat() / (itemCount - 1).coerceAtLeast(1)
        val containerHeight = binding.fastScrollerContainer.height
        val handleHeight = binding.scrollHandle.height
        
        val maxOffset = containerHeight - handleHeight
        val handleOffset = (ratio * maxOffset).toInt()
        
        binding.scrollHandle.translationY = handleOffset.toFloat()
    }
    
    private fun showScrollPreview(position: Int) {
        val messages = chatAdapter.currentList
        if (position in messages.indices) {
            val message = messages[position]
            val preview = if (message.content.length > 100) {
                message.content.substring(0, 100) + "..."
            } else {
                message.content
            }
            
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val time = timeFormat.format(Date(message.timestamp))
            val prefix = if (message.isUser) "我 " else "Grok "
            
            binding.scrollPreview.text = "$prefix$time: $preview"
            binding.scrollPreview.visibility = View.VISIBLE
        }
    }
    
    private fun setupInputArea() {
        binding.btnSend.setOnClickListener {
            val content = binding.editMessage.text.toString().trim()
            if (content.isNotEmpty()) {
                if (viewModel.apiKey.isEmpty()) {
                    showApiKeyDialog()
                    return@setOnClickListener
                }
                binding.editMessage.text?.clear()
                viewModel.sendMessage(content)
            }
        }
    }
    
    private fun setupNewMessageButton() {
        binding.btnScrollToBottom.setOnClickListener {
            val itemCount = chatAdapter.itemCount
            if (itemCount > 0) {
                binding.recyclerMessages.smoothScrollToPosition(itemCount - 1)
            }
            viewModel.clearNewMessageCount()
        }
    }
    
    private fun showApiKeyDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("需要API密钥")
            .setMessage("请先在设置中配置您的API密钥")
            .setPositiveButton("设置") { _, _ ->
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        chatAdapter.submitList(messages) {
                            binding.emptyState.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                            // 滚动条更新
                            binding.recyclerMessages.post {
                                updateScrollHandle()
                            }
                        }
                    }
                }
                
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                        binding.btnSend.isEnabled = !isLoading
                    }
                }
                
                launch {
                    viewModel.error.collect { error ->
                        error?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }
                
                launch {
                    viewModel.newMessageCount.collect { count ->
                        if (count > 0) {
                            binding.newMessageHint.visibility = View.VISIBLE
                            binding.newMessageHint.text = "有 $count 条新消息"
                        } else {
                            binding.newMessageHint.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("消息内容", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    private fun speakMessage(text: String) {
        if (!isTtsReady) {
            Toast.makeText(requireContext(), "语音引擎正在初始化...", Toast.LENGTH_SHORT).show()
            return
        }
        
        val prefs = com.newgrokchat.NewGrokChatApp.instance.prefs
        if (prefs.ttsEnabled) {
            tts?.setSpeechRate(prefs.ttsSpeed)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance_id")
        } else {
            Toast.makeText(requireContext(), "请先在设置中开启语音朗读", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.CHINESE)
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            
            val prefs = com.newgrokchat.NewGrokChatApp.instance.prefs
            if (isTtsReady) {
                tts?.setSpeechRate(prefs.ttsSpeed)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        binding.spinnerModel.setSelection(viewModel.models.indexOf(viewModel.currentModel))
        chatAdapter.notifyDataSetChanged()
    }
    
    override fun onDestroyView() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroyView()
        _binding = null
    }
}
