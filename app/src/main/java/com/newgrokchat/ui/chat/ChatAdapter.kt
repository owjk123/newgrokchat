package com.newgrokchat.ui.chat

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

class ChatAdapter : ListAdapter<Message, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class MessageViewHolder(private val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: Message) {
            binding.messageContent.text = message.content
            
            val layoutParams = binding.messageBubble.layoutParams as FrameLayout.LayoutParams
            if (message.isUser) {
                layoutParams.gravity = Gravity.END
                binding.messageBubble.setBackgroundResource(R.drawable.bg_message_user)
                binding.messageContent.setTextColor(binding.root.context.getColor(R.color.message_user_text))
            } else {
                layoutParams.gravity = Gravity.START
                binding.messageBubble.setBackgroundResource(R.drawable.bg_message_ai)
                binding.messageContent.setTextColor(binding.root.context.getColor(R.color.message_ai_text))
            }
            binding.messageBubble.layoutParams = layoutParams
            
            if (message.isStreaming && message.content.isEmpty()) {
                binding.typingIndicator.visibility = View.VISIBLE
                binding.messageContent.visibility = View.GONE
            } else {
                binding.typingIndicator.visibility = View.GONE
                binding.messageContent.visibility = View.VISIBLE
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
