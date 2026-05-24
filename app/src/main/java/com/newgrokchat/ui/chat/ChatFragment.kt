package com.newgrokchat.ui.chat

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.newgrokchat.R
import com.newgrokchat.databinding.FragmentChatBinding
import com.newgrokchat.ui.MainActivity
import com.newgrokchat.ui.settings.SettingsActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatFragment : Fragment() {
    
    private var _binding: FragmentChatBinding? = null
    // Bug 1修复: binding访问加安全检查
    private val binding get() = _binding!!
    
    // Bug 1修复: ViewModel绑定到Activity
    private lateinit var viewModel: ChatViewModel
    private lateinit var chatAdapter: ChatAdapter
    
    // Bug 2修复: 滚动防抖
    private var isScrolling = false
    private var lastScrollTime = 0L
    private val SCROLL_THROTTLE_MS = 50L
    
    // Bug 3修复: 图片选择
    private val selectedImages = mutableListOf<Uri>()
    private val imageBase64Map = mutableMapOf<Uri, String>() // 缓存base64编码
    
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
        
        // Bug 1修复: ViewModel绑定到Activity，避免Fragment重建丢失状态
        viewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
        
        setupToolbar()
        setupModelSelector()
        setupRecyclerView()
        setupFastScroller()
        setupInputArea()
        setupNewMessageButton()
        observeViewModel()
    }
    
    private fun setupToolbar() {
        // Bug 5修复: 点击logo打开对话列表
        binding.grokLogo.setOnClickListener {
            showConversationList()
        }
        
        binding.btnNewChat.setOnClickListener {
            showNewChatDialog()
        }
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
    }
    
    // Bug 5修复: 显示对话列表
    private fun showConversationList() {
        val dialog = BottomSheetDialog(requireContext())
        val contentView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // 标题
        TextView(requireContext()).apply {
            text = "对话列表"
            textSize = 18f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setTypeface(null, android.graphics.Typeface.BOLD)
            contentView.addView(this)
        }
        
        val conversations = viewModel.conversationList.value
        val currentId = viewModel.conversation.value?.id
        
        if (conversations.isEmpty()) {
            TextView(requireContext()).apply {
                text = "暂无历史对话"
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_secondary, null))
                setPadding(0, 32, 0, 32)
                contentView.addView(this)
            }
        } else {
            for (conv in conversations) {
                val itemView = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 16, 24, 16)
                    val isCurrent = conv.id == currentId
                    if (isCurrent) {
                        setBackgroundColor(resources.getColor(R.color.surface_variant, null))
                    }
                }
                
                TextView(requireContext()).apply {
                    text = conv.title
                    textSize = 15f
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    itemView.addView(this)
                }
                
                val timeFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                val infoText = "${conv.messages.size}条消息 · ${timeFormat.format(Date(conv.updatedAt))}"
                TextView(requireContext()).apply {
                    text = infoText
                    textSize = 12f
                    setTextColor(resources.getColor(R.color.text_hint, null))
                    itemView.addView(this)
                }
                
                // 点击切换对话
                itemView.setOnClickListener {
                    viewModel.switchToConversation(conv.id)
                    dialog.dismiss()
                }
                
                // 长按删除
                itemView.setOnLongClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle("删除对话")
                        .setMessage("确定要删除「${conv.title}」吗？")
                        .setPositiveButton("删除") { _, _ ->
                            viewModel.deleteConversation(conv.id)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    true
                }
                
                contentView.addView(itemView)
                
                // 分隔线
                View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    )
                    setBackgroundColor(resources.getColor(R.color.surface_variant, null))
                    contentView.addView(this)
                }
            }
        }
        
        dialog.setContentView(contentView)
        dialog.show()
    }
    
    private fun showNewChatDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("新对话")
            .setMessage("确定要开始新的对话吗？当前对话将被自动保存。")
            .setPositiveButton("确定") { _, _ ->
                viewModel.newConversation()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun setupModelSelector() {
        val adapter = ArrayAdapter<String>(
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
            getAiAvatar = { viewModel.aiAvatar },
            onImageClick = { imageUri -> showImageViewer(imageUri) }
        )
        
        binding.recyclerMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            
            // Bug 2修复: 滚动监听加安全检查
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    try {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            isScrolling = false
                            _binding?.scrollPreview?.visibility = View.GONE
                            
                            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                            val lastVisible = layoutManager.findLastVisibleItemPosition()
                            val totalItems = layoutManager.itemCount
                            
                            val isAtBottom = lastVisible >= totalItems - 2
                            viewModel.setViewingHistory(!isAtBottom)
                            _binding?.btnScrollToBottom?.visibility = if (isAtBottom) View.GONE else View.VISIBLE
                        }
                    } catch (e: Exception) {
                        // Bug 2修复: 静默处理滚动异常
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
                    // Bug 2修复: 滚动防抖
                    val now = System.currentTimeMillis()
                    if (now - lastScrollTime < SCROLL_THROTTLE_MS) return@setOnTouchListener true
                    lastScrollTime = now
                    
                    isScrolling = true
                    handleFastScroll(event)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isScrolling = false
                    _binding?.scrollPreview?.visibility = View.GONE
                    true
                }
                else -> false
            }
        }
    }
    
    // Bug 2修复: 快速滑动加try-catch和边界检查
    private fun handleFastScroll(event: MotionEvent) {
        try {
            val container = binding.fastScrollerContainer
            val recyclerView = binding.recyclerMessages
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            
            val itemCount = chatAdapter.itemCount
            if (itemCount == 0) return
            
            val containerHeight = container.height.toFloat()
            if (containerHeight <= 0) return
            val relativeY = event.y
            val ratio = (relativeY / containerHeight).coerceIn(0f, 1f)
            
            val targetPosition = (ratio * (itemCount - 1)).toInt().coerceIn(0, itemCount - 1)
            
            // Bug 2修复: 使用scrollToPosition代替scrollToPositionWithOffset
            layoutManager.scrollToPosition(targetPosition)
            
            updateScrollHandle()
            showScrollPreview(targetPosition)
        } catch (e: Exception) {
            // Bug 2修复: 静默处理快速滑动异常
        }
    }
    
    private fun updateScrollHandle() {
        try {
            val itemCount = chatAdapter.itemCount
            if (itemCount == 0) return
            
            val layoutManager = _binding?.recyclerMessages?.layoutManager as? LinearLayoutManager ?: return
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val lastVisible = layoutManager.findLastVisibleItemPosition()
            if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) return
            val currentPosition = (firstVisible + lastVisible) / 2
            
            val ratio = currentPosition.toFloat() / (itemCount - 1).coerceAtLeast(1)
            val containerHeight = _binding?.fastScrollerContainer?.height ?: return
            val handleHeight = _binding?.scrollHandle?.height ?: return
            
            val maxOffset = containerHeight - handleHeight
            val handleOffset = (ratio * maxOffset).toInt()
            
            _binding?.scrollHandle?.translationY = handleOffset.toFloat()
        } catch (e: Exception) {
            // 静默处理
        }
    }
    
    private fun showScrollPreview(position: Int) {
        try {
            val messages = chatAdapter.currentList
            if (position !in messages.indices) return
            val message = messages[position]
            val preview = if (message.content.length > 100) {
                message.content.substring(0, 100) + "..."
            } else {
                message.content
            }
            
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val time = timeFormat.format(Date(message.timestamp))
            val prefix = if (message.isUser) "我 " else "Grok "
            
            _binding?.scrollPreview?.text = "$prefix$time: $preview"
            _binding?.scrollPreview?.visibility = View.VISIBLE
        } catch (e: Exception) {
            // 静默处理
        }
    }
    
    private fun setupInputArea() {
        // Bug 3修复: 附件按钮
        binding.btnAttach.setOnClickListener {
            showAttachmentOptions()
        }
        
        binding.btnSend.setOnClickListener {
            val content = binding.editMessage.text.toString().trim()
            if (content.isEmpty() && selectedImages.isEmpty()) return@setOnClickListener
            
            if (viewModel.apiKey.isEmpty()) {
                showApiKeyDialog()
                return@setOnClickListener
            }
            
            if (!isNetworkAvailable()) {
                Toast.makeText(requireContext(), "网络不可用，请检查网络连接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            binding.editMessage.text?.clear()
            
            // Bug 3修复: 发送时传递图片base64
            val base64List = selectedImages.mapNotNull { imageBase64Map[it] }
            viewModel.setPendingImages(base64List)
            viewModel.sendMessage(content)
            
            // 清除已选图片
            clearSelectedImages()
        }
    }
    
    // Bug 3修复: 附件选项
    private fun showAttachmentOptions() {
        val options = arrayOf("📷 从相册选择", "📸 拍照")
        AlertDialog.Builder(requireContext())
            .setTitle("添加附件")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImageFromGallery()
                    1 -> takePhoto()
                }
            }
            .show()
    }
    
    // Bug 3修复: 从相册选择图片
    private fun pickImageFromGallery() {
        try {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开相册", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Bug 3修复: 拍照
    private fun takePhoto() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(intent, REQUEST_CODE_TAKE_PHOTO)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开相机", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Bug 3修复: 处理图片选择结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != AppCompatActivity.RESULT_OK || data == null) return
        
        when (requestCode) {
            REQUEST_CODE_PICK_IMAGE -> {
                // 处理多选图片
                val clipData = data.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount.coerceAtMost(MAX_IMAGES)) {
                        val uri = clipData.getItemAt(i).uri
                        addSelectedImage(uri)
                    }
                } else {
                    data.data?.let { addSelectedImage(it) }
                }
            }
            REQUEST_CODE_TAKE_PHOTO -> {
                // 拍照结果
                // 注意：拍照返回的缩略图在extras中
                val bitmap = data.extras?.get("data") as? Bitmap
                if (bitmap != null) {
                    val uri = saveBitmapToTemp(bitmap)
                    if (uri != null) addSelectedImage(uri)
                }
            }
        }
    }
    
    // Bug 3修复: 添加选中的图片
    private fun addSelectedImage(uri: Uri) {
        if (selectedImages.size >= MAX_IMAGES) {
            Toast.makeText(requireContext(), "最多选择${MAX_IMAGES}张图片", Toast.LENGTH_SHORT).show()
            return
        }
        if (!selectedImages.contains(uri)) {
            selectedImages.add(uri)
            // 异步压缩并编码base64
            compressAndEncodeImage(uri)
            updateAttachmentPreview()
        }
    }
    
    // Bug 3修复: 压缩图片并转base64
    private fun compressAndEncodeImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream == null) return@launch
                
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (bitmap == null) return@launch
                
                // 压缩到最大1024px
                val maxDim = 1024
                val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                } else {
                    bitmap
                }
                
                // 转为JPEG base64
                val baos = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                imageBase64Map[uri] = base64
                
                if (bitmap !== scaledBitmap) bitmap.recycle()
                scaledBitmap.recycle()
                baos.close()
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "图片处理失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Bug 3修复: 保存拍照Bitmap到临时文件
    private fun saveBitmapToTemp(bitmap: Bitmap): Uri? {
        return try {
            val tempFile = File(requireContext().cacheDir, "photo_${System.currentTimeMillis()}.jpg")
            val fos = java.io.FileOutputStream(tempFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.close()
            Uri.fromFile(tempFile)
        } catch (e: Exception) {
            null
        }
    }
    
    // Bug 3修复: 更新附件预览
    private fun updateAttachmentPreview() {
        val container = _binding?.attachmentPreviewContainer ?: return
        container.removeAllViews()
        
        if (selectedImages.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        
        container.visibility = View.VISIBLE
        
        for (uri in selectedImages) {
            val itemView = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                    marginEnd = 8
                }
            }
            
            ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(120, 120)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(uri)
                setBackgroundColor(resources.getColor(R.color.surface_variant, null))
                itemView.addView(this)
            }
            
            // 删除按钮
            TextView(requireContext()).apply {
                text = "✕"
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_primary, null))
                setBackgroundColor(resources.getColor(R.color.error_red, null))
                setPadding(4, 2, 4, 2)
                gravity = android.view.Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.END or android.view.Gravity.TOP
                )
                setOnClickListener {
                    selectedImages.remove(uri)
                    imageBase64Map.remove(uri)
                    updateAttachmentPreview()
                }
                itemView.addView(this)
            }
            
            container.addView(itemView)
        }
    }
    
    // Bug 3修复: 清除已选图片
    private fun clearSelectedImages() {
        selectedImages.clear()
        imageBase64Map.clear()
        updateAttachmentPreview()
    }
    
    // Bug 3修复: 图片查看器
    private fun showImageViewer(imageUri: String) {
        try {
            val dialog = AlertDialog.Builder(requireContext())
                .setView(ImageView(requireContext()).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(resources.getColor(R.color.background, null))
                    if (imageUri.startsWith("file://") || imageUri.startsWith("content://")) {
                        setImageURI(Uri.parse(imageUri))
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                })
                .setNegativeButton("关闭", null)
                .create()
            dialog.show()
        } catch (e: Exception) {
            // 静默处理
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
    
    private fun showRetryDialog(errorMessage: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("发送失败")
            .setMessage(errorMessage)
            .setPositiveButton("重试") { _, _ ->
                viewModel.retryLastMessage()
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
                            _binding?.emptyState?.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                            _binding?.recyclerMessages?.post {
                                updateScrollHandle()
                            }
                        }
                    }
                }
                
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        _binding?.progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
                        _binding?.btnSend?.isEnabled = !isLoading
                        _binding?.waitingHint?.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                
                launch {
                    viewModel.error.collect { error ->
                        error?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                            if (viewModel.canRetry) {
                                showRetryDialog(it)
                            }
                            viewModel.clearError()
                        }
                    }
                }
                
                launch {
                    viewModel.timeoutMessage.collect { timeoutMsg ->
                        timeoutMsg?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                            if (viewModel.canRetry) {
                                showRetryDialog(it)
                            }
                            viewModel.clearTimeoutMessage()
                        }
                    }
                }
                
                launch {
                    viewModel.newMessageCount.collect { count ->
                        if (count > 0) {
                            _binding?.newMessageHint?.visibility = View.VISIBLE
                            _binding?.newMessageHint?.text = "有 $count 条新消息"
                        } else {
                            _binding?.newMessageHint?.visibility = View.GONE
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
    
    // Bug 6修复: 语音朗读通过Activity的TTS
    private fun speakMessage(text: String) {
        val activity = activity as? MainActivity
        if (activity == null) {
            Toast.makeText(requireContext(), "无法使用语音功能", Toast.LENGTH_SHORT).show()
            return
        }
        
        val prefs = com.newgrokchat.NewGrokChatApp.instance.prefs
        if (!prefs.ttsEnabled) {
            // 提示用户开启TTS
            AlertDialog.Builder(requireContext())
                .setTitle("语音朗读未开启")
                .setMessage("请先在设置中开启语音朗读功能")
                .setPositiveButton("去设置") { _, _ ->
                    startActivity(Intent(requireContext(), SettingsActivity::class.java))
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        
        if (!activity.isTtsDataAvailable()) {
            AlertDialog.Builder(requireContext())
                .setTitle("语音引擎不可用")
                .setMessage("您的设备可能未安装中文语音数据，是否前往系统设置安装？")
                .setPositiveButton("去设置") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "无法打开系统设置", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        
        activity.speakText(text)
    }
    
    // Bug 1修复: onResume不再调用notifyDataSetChanged（与ListAdapter不兼容）
    override fun onResume() {
        super.onResume()
        binding.spinnerModel.setSelection(viewModel.models.indexOf(viewModel.currentModel))
    }
    
    override fun onDestroyView() {
        // Bug 1修复: 彻底清理，防止内存泄漏和NPE
        _binding = null
        super.onDestroyView()
    }
    
    companion object {
        private const val REQUEST_CODE_PICK_IMAGE = 1001
        private const val REQUEST_CODE_TAKE_PHOTO = 1002
        private const val MAX_IMAGES = 3
    }
}
