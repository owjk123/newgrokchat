package com.newgrokchat.ui.chat

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
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
import com.newgrokchat.databinding.FragmentChatBinding
import com.newgrokchat.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {
    
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: ChatViewModel
    private lateinit var chatAdapter: ChatAdapter
    
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
        setupInputArea()
        observeViewModel()
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
        chatAdapter = ChatAdapter()
        binding.recyclerMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
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
    
    private fun showApiKeyDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("API Key Required")
            .setMessage("Please set your API key in Settings first.")
            .setPositiveButton("Settings") { _, _ ->
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        chatAdapter.submitList(messages) {
                            if (messages.isNotEmpty()) {
                                binding.recyclerMessages.smoothScrollToPosition(messages.size - 1)
                            }
                        }
                        binding.emptyState.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
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
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        binding.spinnerModel.setSelection(viewModel.models.indexOf(viewModel.currentModel))
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
