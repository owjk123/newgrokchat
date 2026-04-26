package com.newgrokchat.ui.chat

import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.newgrokchat.R
import com.newgrokchat.databinding.ItemMessageBinding
import com.newgrokchat.model.Message

class ChatAdapter(
    private val onCopyClick: (Message) -> Unit,
    private val onSpeakClick: (Message) -> Unit,
    private val getAiAvatar: () -> String
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
            
            val layoutParams = binding.messageBubble.layoutParams as FrameLayout.LayoutParams
            
            if (message.isUser) {
                // 用户消息：靠右
                layoutParams.gravity = Gravity.END
                binding.messageBubble.setBackgroundResource(R.drawable.bg_message_user)
                binding.messageContent.setTextColor(binding.root.context.getColor(R.color.message_user_text))
                binding.aiAvatarContainer.visibility = View.GONE
            } else {
                // AI消息：靠左，显示头像
                layoutParams.gravity = Gravity.START
                binding.messageBubble.setBackgroundResource(R.drawable.bg_message_ai)
                binding.messageContent.setTextColor(binding.root.context.getColor(R.color.message_ai_text))
                binding.aiAvatarContainer.visibility = View.VISIBLE
                
                // 显示AI头像
                val aiAvatar = getAiAvatar()
                if (aiAvatar.startsWith("http") || aiAvatar.startsWith("content://") || aiAvatar.startsWith("file://")) {
                    try {
                        binding.aiAvatarText.visibility = View.GONE
                        binding.aiAvatarImage.visibility = View.VISIBLE
                        binding.aiAvatarImage.setImageURI(Uri.parse(aiAvatar))
                    } catch (e: Exception) {
                        binding.aiAvatarText.visibility = View.VISIBLE
                        binding.aiAvatarImage.visibility = View.GONE
                        binding.aiAvatarText.text = "🤖"
                    }
                } else {
                    binding.aiAvatarText.visibility = View.VISIBLE
                    binding.aiAvatarImage.visibility = View.GONE
                    binding.aiAvatarText.text = aiAvatar
                }
            }
            binding.messageBubble.layoutParams = layoutParams
            
            // typing indicator
            if (message.isStreaming && message.content.isEmpty()) {
                binding.typingIndicator.visibility = View.VISIBLE
                binding.messageContent.visibility = View.GONE
                binding.actionButtons.visibility = View.GONE
            } else {
                binding.typingIndicator.visibility = View.GONE
                binding.messageContent.visibility = View.VISIBLE
                // 显示/隐藏操作按钮
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
    
    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}
