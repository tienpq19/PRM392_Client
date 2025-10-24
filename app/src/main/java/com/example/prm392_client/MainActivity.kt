package com.example.prm392_client

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.prm392_client.databinding.ActivityMainBinding
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var hubConnection: HubConnection
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        /* start */
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val BASE_URL = "https://8z61f79f-8000.asse.devtunnels.ms"
        hubConnection = HubConnectionBuilder.create("$BASE_URL/chatHub").build()

        // Receive message from server
        hubConnection.on("ReceiveMessage", { user: String, message: String ->
            uiScope.launch {
                addMessage("$user: $message")
            }
        }, String::class.java, String::class.java)

        // Connect using coroutine
        uiScope.launch { connectToHub() }

        // Send message when button clicked
        binding.btnSend.setOnClickListener {
            uiScope.launch { sendMessage() }
        }
    }

    private suspend fun connectToHub() {
        withContext(Dispatchers.IO) {
            try {
                hubConnection.start().blockingAwait()
                withContext(Dispatchers.Main) {
                    addMessage("Connected to SignalR hub")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addMessage("Connection failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun sendMessage() {
        val user = binding.etUser.text.toString().ifEmpty { "Anonymous" }
        val message = binding.etMessage.text.toString()

        if (message.isBlank()) return

        if (hubConnection.connectionState == HubConnectionState.CONNECTED) {
            withContext(Dispatchers.IO) {
                hubConnection.send("SendMessage", user, message)
            }
            binding.etMessage.text.clear()
        } else {
            addMessage("Not connected to hub")
        }
    }

    private fun addMessage(text: String) {
        val tv = TextView(this)
        tv.text = text
        binding.messageContainer.addView(tv)
        binding.messageScroll.post {
            binding.messageScroll.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.launch(Dispatchers.IO) {
            if (hubConnection.connectionState == HubConnectionState.CONNECTED) {
                hubConnection.stop()
            }
        }
        job.cancel()
    }
}