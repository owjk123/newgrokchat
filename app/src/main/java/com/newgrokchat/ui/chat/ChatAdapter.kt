package com.newgrokchat.ui.chat

import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.newgrokchat.R
import com.newgrokchat.databinding.ItemMessageBinding
import com.newgrokchat.model.Message
import java.io.File

class ChatAdapter(
    private val onCopyClick: (Message) -> Unit,
    private val onSpeakClick: (Message) -> Unit,
    private val getAiAvatar: () -> String,
    // Bug 3修复: 图片点击回调
    private val onImageClick: (String) -> Unit = {}
) : ListAdapter<Message, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    private var showActionsForPosition: Int = -1
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }
    
    inner class MessageViewHolder(private val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: Message, position: Int) {
            binding.messageContent.text = message.content
            
            val layoutParams = binding.messageBubble.layoutParams as LinearLayout.LayoutParams
            
            if (message.isUser) {
                layoutParams.gravity = Gravity.END
                binding.messageBubble.setBackgroundResource(R.drawable.bg_message_user)
                binding.messageContent.setTextColor(binding.root.context.getColor(R.color.message_user_text))
                binding.aiAvatarContainer.visibility = View.GONE
            } else {
                layoutParams.gravity = Gravity.START
                binding.messageBubble.setBackgroundResource(R.drawable.bg_message_ai)
                binding.messageContent.setTextColor(binding.root.context.getColor(R.color.message_ai_text))
                binding.aiAvatarContainer.visibility = View.VISIBLE
                
                // Bug 4修复: 从内部存储加载头像
                val aiAvatar = getAiAvatar()
                if (aiAvatar.startsWith("file://")) {
                    try {
                        val file = File(Uri.parse(aiAvatar).path ?: "")
                        if (file.exists()) {
                            binding.aiAvatarText.visibility = View.GONE
                            binding.aiAvatarImage.visibility = View.VISIBLE
                            binding.aiAvatarImage.setImageURI(Uri.fromFile(file))
                        } else {
                            showDefaultAvatar(binding)
                        }
                    } catch (e: Exception) {
                        showDefaultAvatar(binding)
                    }
                } else if (aiAvatar.startsWith("http") || aiAvatar.startsWith("content://")) {
                    try {
                        binding.aiAvatarText.visibility = View.GONE
                        binding.aiAvatarImage.visibility = View.VISIBLE
                        binding.aiAvatarImage.setImageURI(Uri.parse(aiAvatar))
                    } catch (e: Exception) {
                        showDefaultAvatar(binding)
                    }
                } else {
                    binding.aiAvatarText.visibility = View.VISIBLE
                    binding.aiAvatarImage.visibility = View.GONE
                    binding.aiAvatarText.text = if (aiAvatar.isNotEmpty()) aiAvatar else "🤖"
                }
            }
            binding.messageBubble.layoutParams = layoutParams
            
            // Bug 3修复: 显示消息中的图片
            if (message.isUser && message.imageUris.isNotEmpty()) {
                binding.imageContainer.visibility = View.VISIBLE
                // 显示最多3张图片缩略图
                val imageViews = listOf(binding.messageImage1, binding.messageImage2, binding.messageImage3)
                for (i in imageViews.indices) {
                    if (i < message.imageUris.size && i < 3) {
                        imageViews[i].visibility = View.VISIBLE
                        try {
                            val uri = message.imageUris[i]
                            // 本地文件URI直接加载
                            if (uri.startsWith("file://") || uri.startsWith("content://")) {
                                imageViews[i].setImageURI(Uri.parse(uri))
                            }
                        } catch (e: Exception) {
                            imageViews[i].visibility = View.GONE
                        }
                        // 点击查看大图
                        val imageUri = message.imageUris[i]
                        imageViews[i].setOnClickListener {
                            onImageClick(imageUri)
                        }
                    } else {
                        imageViews[i].visibility = View.GONE
                    }
                }
            } else {
                binding.imageContainer.visibility = View.GONE
            }
            
            // typing indicator
            if (message.isStreaming && message.content.isEmpty()) {
                binding.typingIndicator.visibility = View.VISIBLE
                binding.messageContent.visibility = View.GONE
                binding.actionButtons.visibility = View.GONE
            } else {
                binding.typingIndicator.visibility = View.GONE
                binding.messageContent.visibility = View.VISIBLE
                binding.actionButtons.visibility = if (showActionsForPosition == position) View.VISIBLE else View.GONE
            }
            
            // 按钮点击
            binding.btnCopy.setOnClickListener { 
                onCopyClick(message)
                showActionsForPosition = -1
                notifyItemChanged(position)
            }
            binding.btnSpeak.setOnClickListener { 
                onSpeakClick(message)
            }
            
            // 长按显示/隐藏操作按钮
            binding.messageBubble.setOnLongClickListener {
                showActionsForPosition = if (showActionsForPosition == position) -1 else position
                notifyItemChanged(position)
                true
            }
            
            // 点击消息气泡隐藏操作按钮
            binding.messageBubble.setOnClickListener {
                if (showActionsForPosition == position) {
                    showActionsForPosition = -1
                    notifyItemChanged(position)
                }
            }
        }
    }
    
    private fun showDefaultAvatar(binding: ItemMessageBinding) {
        binding.aiAvatarText.visibility = View.VISIBLE
        binding.aiAvatarImage.visibility = View.GONE
        binding.aiAvatarText.text = "🤖"
    }
    
    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}
