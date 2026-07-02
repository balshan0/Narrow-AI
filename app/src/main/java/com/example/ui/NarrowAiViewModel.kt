package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.ChatMessage
import com.example.data.ChatRepository
import com.example.data.Content
import com.example.data.GeminiRequest
import com.example.data.Part
import com.example.data.InlineData
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.net.Uri
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class NarrowMode(
    val id: String,
    val title: String,
    val description: String,
    val systemPrompt: String,
    val initialSuggestion: String,
    val suggestions: List<String>
) {
    NARROW_AI(
        id = "narrow_ai",
        title = "Narrow AI",
        description = "A highly specialized, minimal focus assistant.",
        systemPrompt = "You are Narrow AI, a highly specialized, minimal, and focused AI assistant. Keep your answers short, direct, and conversational (1-3 sentences maximum). Avoid long preambles, excessive explanations, or bulleted lists unless explicitly asked.",
        initialSuggestion = "What shall we focus on today?",
        suggestions = listOf(
            "Explain quantum computing simply",
            "Optimize a Room database query",
            "Write a concise professional email",
            "Solve: 3x + 12 = 45"
        )
    )
}

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    data class Speaking(val text: String) : VoiceState()
    data class Error(val message: String) : VoiceState()
}

class NarrowAiViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "narrow_ai_database"
    ).build()

    private val repository = ChatRepository(db.chatDao())

    val chatMessages: StateFlow<List<ChatMessage>> = repository.allMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedMode = MutableStateFlow(NarrowMode.NARROW_AI)
    val selectedMode: StateFlow<NarrowMode> = _selectedMode.asStateFlow()

    private val _selectedModelName = MutableStateFlow("gemini-2.5-flash") // Standard vs Flash-Lite (gemini-2.5-flash-8b)
    val selectedModelName: StateFlow<String> = _selectedModelName.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Real-time word-by-word streaming message state
    private val _streamingMessage = MutableStateFlow<ChatMessage?>(null)
    val streamingMessage: StateFlow<ChatMessage?> = _streamingMessage.asStateFlow()

    // Native Voice & Speech Recognition state
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _isVoiceModeEnabled = MutableStateFlow(false)
    val isVoiceModeEnabled: StateFlow<Boolean> = _isVoiceModeEnabled.asStateFlow()

    // Attached Files Support for Narrow AI
    data class AttachedFile(
        val name: String,
        val type: String, // PDF, CSV, Kotlin, Text, Image
        val content: String,
        val iconType: String // "pdf", "csv", "code", "text", "image"
    )

    private val _attachedFiles = MutableStateFlow<List<AttachedFile>>(emptyList())
    val attachedFiles: StateFlow<List<AttachedFile>> = _attachedFiles.asStateFlow()

    fun attachFile(file: AttachedFile) {
        val current = _attachedFiles.value
        if (!current.any { it.name == file.name }) {
            _attachedFiles.value = current + file
        }
    }

    fun attachCameraPhoto(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                val byteArray = outputStream.toByteArray()
                val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                val fileName = "Camera_${System.currentTimeMillis()}.jpg"
                val attachedFile = AttachedFile(
                    name = fileName,
                    type = "Camera Photo",
                    content = "Base64ImageData:$base64String",
                    iconType = "image"
                )
                withContext(Dispatchers.Main) {
                    attachFile(attachedFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun attachFileFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                var fileName = "file_${System.currentTimeMillis()}"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex)
                    }
                }

                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val typeName = when {
                    mimeType.startsWith("image/") -> "Image File"
                    mimeType.contains("pdf", true) -> "PDF Document"
                    mimeType.contains("csv", true) -> "CSV Spreadsheet"
                    mimeType.contains("json", true) -> "JSON Data"
                    mimeType.contains("text", true) || mimeType.contains("plain", true) -> "Text Document"
                    else -> "Document File"
                }

                val iconType = when {
                    mimeType.startsWith("image/") -> "image"
                    mimeType.contains("pdf", true) -> "pdf"
                    mimeType.contains("csv", true) -> "csv"
                    mimeType.contains("json", true) || mimeType.contains("javascript", true) || mimeType.contains("kotlin", true) -> "code"
                    else -> "text"
                }

                var contentString = ""
                if (mimeType.startsWith("image/")) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        val base64String = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        contentString = "Base64ImageData:$base64String"
                    }
                } else {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        // Limit to 50KB to keep prompt token size reasonable
                        contentString = if (bytes.size > 51200) {
                            String(bytes.take(51200).toByteArray()) + "\n[Truncated: File too large]"
                        } else {
                            String(bytes)
                        }
                    }
                }

                val attachedFile = AttachedFile(
                    name = fileName,
                    type = typeName,
                    content = contentString,
                    iconType = iconType
                )

                withContext(Dispatchers.Main) {
                    attachFile(attachedFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun detachFile(file: AttachedFile) {
        _attachedFiles.value = _attachedFiles.value.filterNot { it.name == file.name }
    }

    fun clearAttachedFiles() {
        _attachedFiles.value = emptyList()
    }

    // Safe detection if Firebase is available/configured without crashing
    private val isFirebaseAvailable: Boolean by lazy {
        try {
            com.google.firebase.FirebaseApp.getInstance()
            true
        } catch (e: Throwable) {
            false
        }
    }

    private val auth: FirebaseAuth? by lazy {
        if (isFirebaseAvailable) {
            try {
                FirebaseAuth.getInstance()
            } catch (e: Throwable) {
                null
            }
        } else {
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        if (isFirebaseAvailable) {
            try {
                FirebaseFirestore.getInstance()
            } catch (e: Throwable) {
                null
            }
        } else {
            null
        }
    }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Isolated OkHttpClient for direct low-latency line-by-line streams
    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        // Initialize Speech Engines
        textToSpeech = TextToSpeech(application, this)
        _currentUser.value = auth?.currentUser
        if (auth?.currentUser != null) {
            syncAllMessages()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
        } else {
            _voiceState.value = VoiceState.Error("TTS Engine initialization failed.")
        }
    }

    fun selectMode(mode: NarrowMode) {
        _selectedMode.value = mode
    }

    fun selectModel(modelName: String) {
        _selectedModelName.value = modelName
    }

    fun setVoiceModeEnabled(enabled: Boolean) {
        _isVoiceModeEnabled.value = enabled
        if (!enabled) {
            textToSpeech?.stop()
            stopListening()
            _voiceState.value = VoiceState.Idle
        }
    }

    // Toggle speech-to-text
    fun toggleListening(context: Context) {
        if (_voiceState.value is VoiceState.Listening) {
            stopListening()
        } else {
            startListening(context)
        }
    }

    private fun startListening(context: Context) {
        textToSpeech?.stop()
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _voiceState.value = VoiceState.Listening
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val errMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions missing"
                    SpeechRecognizer.ERROR_NETWORK -> "Network failure"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No voice matched"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    else -> "Unknown recognizer error"
                }
                _voiceState.value = VoiceState.Error(errMsg)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull() ?: ""
                _voiceState.value = VoiceState.Idle
                if (spokenText.isNotBlank()) {
                    sendMessage(spokenText)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull() ?: ""
                if (partialText.isNotBlank()) {
                    _voiceState.value = VoiceState.Listening
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _voiceState.value = VoiceState.Idle
    }

    fun speakResponse(text: String) {
        _voiceState.value = VoiceState.Speaking(text)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NarrowAiResponseID")
    }

    // Google / Anonymous Secure Auth
    fun signInAnonymously() {
        val currentAuth = auth
        if (currentAuth == null) {
            _errorMessage.value = "Firebase connection warning. Offline mode active."
            return
        }
        _isSyncing.value = true
        viewModelScope.launch {
            try {
                currentAuth.signInAnonymously()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            _currentUser.value = currentAuth.currentUser
                            syncAllMessages()
                        } else {
                            _errorMessage.value = "Sign in failed: ${task.exception?.message}"
                            _isSyncing.value = false
                        }
                    }
            } catch (e: Exception) {
                _errorMessage.value = "Firebase connection warning. Offline mode active."
                _isSyncing.value = false
            }
        }
    }

    fun signOut() {
        auth?.signOut()
        _currentUser.value = null
    }

    // Sync cloud data with Firestore
    private fun syncMessageToFirestore(message: ChatMessage) {
        val currentAuth = auth ?: return
        val currentFirestore = firestore ?: return
        val user = currentAuth.currentUser ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = hashMapOf(
                    "role" to message.role,
                    "message" to message.message,
                    "timestamp" to message.timestamp,
                    "mode" to message.mode
                )
                currentFirestore.collection("users")
                    .document(user.uid)
                    .collection("messages")
                    .add(data)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun syncAllMessages() {
        val currentAuth = auth ?: return
        val currentFirestore = firestore ?: return
        val user = currentAuth.currentUser ?: return
        _isSyncing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localMessages = chatMessages.value
                localMessages.forEach { msg ->
                    currentFirestore.collection("users")
                        .document(user.uid)
                        .collection("messages")
                        .document(msg.timestamp.toString())
                        .set(hashMapOf(
                            "role" to msg.role,
                            "message" to msg.message,
                            "timestamp" to msg.timestamp,
                            "mode" to msg.mode
                        ))
                }

                currentFirestore.collection("users")
                    .document(user.uid)
                    .collection("messages")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        viewModelScope.launch {
                            snapshot.documents.forEach { doc ->
                                val role = doc.getString("role") ?: "user"
                                val message = doc.getString("message") ?: ""
                                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                val mode = doc.getString("mode") ?: "General Helper"

                                val exists = chatMessages.value.any { it.message == message && it.timestamp == timestamp }
                                if (!exists) {
                                    repository.insertMessage(
                                        ChatMessage(
                                            role = role,
                                            message = message,
                                            timestamp = timestamp,
                                            mode = mode
                                        )
                                    )
                                }
                            }
                            _isSyncing.value = false
                        }
                    }
                    .addOnFailureListener {
                        _isSyncing.value = false
                    }
            } catch (e: Exception) {
                _isSyncing.value = false
                e.printStackTrace()
            }
        }
    }

    // Modern Lightning-fast Streaming Request
    fun sendMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return

        val currentMode = _selectedMode.value
        val modelName = _selectedModelName.value // "gemini-2.5-flash" or "gemini-2.5-flash-8b"
        val apiKey = BuildConfig.GEMINI_API_KEY

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null

            // 1. Immediately insert user message locally
            val userMsg = ChatMessage(
                role = "user",
                message = trimmedText,
                mode = currentMode.title
            )
            repository.insertMessage(userMsg)
            syncMessageToFirestore(userMsg)

            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                _errorMessage.value = "API_KEY_MISSING"
                _isLoading.value = false
                return@launch
            }

            // 2. Build history payload including the newly created message
            val currentHistory = chatMessages.value
                .filter { it.mode == currentMode.title }
                .toMutableList()
            if (currentHistory.none { it.message == trimmedText && it.role == "user" }) {
                currentHistory.add(userMsg)
            }

            val attached = _attachedFiles.value
            val contents = currentHistory.map { msg ->
                val partsList = mutableListOf<Part>()
                if (msg.message == trimmedText && msg.role == "user") {
                    val nonImageFiles = attached.filter { !it.content.startsWith("Base64ImageData:") }
                    val imageFiles = attached.filter { it.content.startsWith("Base64ImageData:") }

                    // Build non-image files text context
                    val filesContextText = if (nonImageFiles.isNotEmpty()) {
                        val filesContext = nonImageFiles.joinToString(separator = "\n") { file ->
                            "[Attached File: ${file.name} (${file.type})]\nContent:\n${file.content}\n---"
                        }
                        "Here are the attached files:\n$filesContext\n\nUser Question:\n${msg.message}"
                    } else {
                        msg.message
                    }

                    partsList.add(Part(text = filesContextText))

                    // Add image files as real multi-modal parts!
                    imageFiles.forEach { imgFile ->
                        val base64Data = imgFile.content.substringAfter("Base64ImageData:")
                        val mimeType = when {
                            imgFile.name.endsWith(".png", true) -> "image/png"
                            imgFile.name.endsWith(".webp", true) -> "image/webp"
                            else -> "image/jpeg"
                        }
                        partsList.add(Part(inlineData = InlineData(mimeType = mimeType, data = base64Data)))
                    }
                } else {
                    partsList.add(Part(text = msg.message))
                }

                Content(
                    role = if (msg.role == "user") "user" else "model",
                    parts = partsList
                )
            }

            val geminiRequest = GeminiRequest(
                contents = contents,
                systemInstruction = Content(
                    parts = listOf(Part(text = currentMode.systemPrompt))
                )
            )

            // Serialize Request
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(GeminiRequest::class.java)
            val jsonPayload = adapter.toJson(geminiRequest)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:streamGenerateContent?key=$apiKey"
            val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
            val okHttpRequest = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            try {
                val response = streamClient.newCall(okHttpRequest).execute()
                val responseBody = response.body
                if (!response.isSuccessful || responseBody == null) {
                    _errorMessage.value = "Server returned failure code: ${response.code}"
                    _isLoading.value = false
                    return@launch
                }

                // Initialize empty placeholder for real-time word-by-word streaming updates
                val tempMsg = ChatMessage(
                    role = "model",
                    message = "",
                    mode = currentMode.title,
                    timestamp = System.currentTimeMillis()
                )
                _streamingMessage.value = tempMsg

                val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
                var line: String?
                var accumulatedText = ""

                while (reader.readLine().also { line = it } != null) {
                    val rawLine = line ?: ""
                    val extractedText = extractTextFromChunk(rawLine)
                    if (extractedText.isNotEmpty()) {
                        accumulatedText += extractedText
                        _streamingMessage.value = _streamingMessage.value?.copy(message = accumulatedText)
                    }
                }

                // Finalize response
                val finalResponseText = accumulatedText.trim()
                _streamingMessage.value = null

                if (finalResponseText.isNotEmpty()) {
                    val finalModelMsg = ChatMessage(
                        role = "model",
                        message = finalResponseText,
                        mode = currentMode.title
                    )
                    repository.insertMessage(finalModelMsg)
                    syncMessageToFirestore(finalModelMsg)

                    // Speak out response if TTS is activated
                    if (_isVoiceModeEnabled.value) {
                        speakResponse(finalResponseText)
                    }
                } else {
                    _errorMessage.value = "Empty response received. Please check connection."
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Network latency issue or secure timeout occurred."
            } finally {
                _isLoading.value = false
                _streamingMessage.value = null
                clearAttachedFiles()
            }
        }
    }

    private fun extractTextFromChunk(chunk: String): String {
        // Safe robust parsing independent of JSON syntax fragments
        val regex = "\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
        val matches = regex.findAll(chunk)
        val sb = java.lang.StringBuilder()
        for (match in matches) {
            val rawText = match.groups[1]?.value ?: ""
            sb.append(unescapeJson(rawText))
        }
        return sb.toString()
    }

    private fun unescapeJson(escaped: String): String {
        val builder = StringBuilder()
        var i = 0
        while (i < escaped.length) {
            val c = escaped[i]
            if (c == '\\' && i + 1 < escaped.length) {
                val next = escaped[i + 1]
                when (next) {
                    'n' -> builder.append('\n')
                    't' -> builder.append('\t')
                    'r' -> builder.append('\r')
                    'b' -> builder.append('\b')
                    'f' -> builder.append('\u000c')
                    '"' -> builder.append('"')
                    '\\' -> builder.append('\\')
                    'u' -> {
                        if (i + 5 < escaped.length) {
                            try {
                                val hex = escaped.substring(i + 2, i + 6)
                                builder.append(hex.toInt(16).toChar())
                                i += 4
                            } catch (e: Exception) {
                                builder.append("\\u")
                            }
                        } else {
                            builder.append("\\u")
                        }
                    }
                    else -> builder.append(next)
                }
                i += 2
            } else {
                builder.append(c)
                i++
            }
        }
        return builder.toString()
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
    }
}
