package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ChatMessage
import android.graphics.Bitmap
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.*

// Absolute Monochrome Minimalist Palette (ChatGPT inspired)
val ObsidianBg = Color(0xFF000000) // Pitch Black OLED Background
val SurfaceCard = Color(0xFF171717) // ChatGPT Dark Grey for control panels/menus
val SlateBorder = Color(0xFF262626) // Sleek borders and lines
val NeonCyan = Color(0xFFFFFFFF) // Pure White for high-contrast typography and accents
val ElectricBlue = Color(0xFF212121) // User Chat bubble background
val ActiveGreen = Color(0xFFFFFFFF) // Active white indicators
val MutedText = Color(0xFF9B9B9B) // Warm Slate/Muted Grey for helper prompts and subtexts
val TextLight = Color(0xFFECECEC) // Elegant Soft White for reading messages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NarrowAiScreen(
    viewModel: NarrowAiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val activeMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val activeModel by viewModel.selectedModelName.collectAsStateWithLifecycle()
    val streamingMessage by viewModel.streamingMessage.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorState by viewModel.errorMessage.collectAsStateWithLifecycle()

    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val isVoiceModeEnabled by viewModel.isVoiceModeEnabled.collectAsStateWithLifecycle()

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val attachedFiles by viewModel.attachedFiles.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    var textInput by remember { mutableStateOf("") }
    var isPanelExpanded by remember { mutableStateOf(false) }
    var showFilePicker by remember { mutableStateOf(false) }

    // Speech-to-Text permissions launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleListening(context)
        }
    }

    // Camera capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.attachCameraPhoto(bitmap)
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
        }
    }

    // File selection launcher
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.attachFileFromUri(context, uri)
        }
    }

    // Scroll to latest message whenever message list size or streaming text updates
    LaunchedEffect(messages.size, streamingMessage, isLoading) {
        val filteredCount = messages.filter { it.mode == activeMode.title }.size
        if (filteredCount > 0 || streamingMessage != null) {
            val lastIdx = if (streamingMessage != null) filteredCount else filteredCount - 1
            if (lastIdx >= 0) {
                listState.animateScrollToItem(lastIdx)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Clean Equal Sign Menu Button on left (ChatGPT style circular menu button)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceCard)
                                .clickable { isPanelExpanded = !isPanelExpanded }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Specialized Modes Menu",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Column {
                            Text(
                                text = activeMode.title,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                color = TextLight,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (activeModel == "gemini-2.5-flash-8b") "Flash-Lite" else "Standard Engine",
                                color = MutedText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                },
                actions = {
                    // ChatGPT-like Top Right Capsule Action Button
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceCard)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Clear Chat Button (represented by clean edit pencil icon)
                        IconButton(
                            onClick = { viewModel.clearHistory() },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("clear_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = "Start New Chat",
                                tint = TextLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(16.dp)
                                .width(1.dp)
                                .background(SlateBorder)
                        )

                        // Settings Panel Trigger
                        IconButton(
                            onClick = { isPanelExpanded = !isPanelExpanded },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("toggle_settings_button")
                        ) {
                            Icon(
                                imageVector = if (isPanelExpanded) Icons.Default.Close else Icons.Default.MoreVert,
                                contentDescription = "More Options Panel",
                                tint = TextLight,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianBg,
                    titleContentColor = TextLight
                )
            )
        },
        containerColor = ObsidianBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Expandable settings and feature panel
            AnimatedVisibility(
                visible = isPanelExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("settings_expanded_panel"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "NARROW SYSTEM MANAGEMENT",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        HorizontalDivider(color = SlateBorder)

                        // Engine Selectors
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Model Optimization", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Toggle between low-latency and heavy reasoning engines.", color = MutedText, fontSize = 10.sp)
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ObsidianBg)
                                    .padding(2.dp)
                            ) {
                                val isFlash = activeModel == "gemini-2.5-flash-8b"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isFlash) SurfaceCard else Color.Transparent)
                                        .clickable { viewModel.selectModel("gemini-2.5-flash-8b") }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Flash-Lite", color = if (isFlash) NeonCyan else MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (!isFlash) SurfaceCard else Color.Transparent)
                                        .clickable { viewModel.selectModel("gemini-3.5-flash") }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Standard", color = if (!isFlash) NeonCyan else MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Voice mode toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Speech Vocalization", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Read all answers aloud in conversational audio.", color = MutedText, fontSize = 10.sp)
                            }
                            Switch(
                                checked = isVoiceModeEnabled,
                                onCheckedChange = { viewModel.setVoiceModeEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianBg,
                                    checkedTrackColor = NeonCyan,
                                    uncheckedThumbColor = MutedText,
                                    uncheckedTrackColor = SurfaceCard,
                                    uncheckedBorderColor = SlateBorder
                                )
                            )
                        }

                        HorizontalDivider(color = SlateBorder)

                        // Auth section
                        if (currentUser != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudQueue,
                                        contentDescription = "Cloud Connected",
                                        tint = ActiveGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Securely Backed Up",
                                            color = TextLight,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "UID: ${currentUser?.uid?.take(8)}...",
                                            color = MutedText,
                                            fontSize = 9.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = { viewModel.signOut() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A1C1C)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Disconnect", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Backup your conversations securely in the Firestore cloud to read them across devices.",
                                    color = MutedText,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )

                                Button(
                                    onClick = { viewModel.signInAnonymously() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianBg),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(
                                            color = ObsidianBg,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.CloudSync,
                                            contentDescription = "Connect Profile",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sign In with Secure Firestore", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // Chat & Suggestions Section
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Filter messages corresponding to currently selected specialized mode
                val filteredMessages = messages.filter { it.mode == activeMode.title }

                if (filteredMessages.isEmpty() && streamingMessage == null) {
                    // Empty Screen / Onboarding ChatGPT Suggestion State
                    OnboardingEmptyState(
                        mode = activeMode,
                        onSuggestionClick = { suggestion ->
                            textInput = suggestion
                            viewModel.sendMessage(suggestion)
                            textInput = ""
                        }
                    )
                } else {
                    // Messages List
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(filteredMessages) { msg ->
                            MessageBubble(
                                message = msg,
                                onSpeak = { viewModel.speakResponse(it) },
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(msg.message))
                                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // Render the real-time word-by-word streaming message at the end
                        streamingMessage?.let { streamMsg ->
                            item {
                                MessageBubble(
                                    message = streamMsg,
                                    onSpeak = { viewModel.speakResponse(it) },
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(streamMsg.message))
                                    }
                                )
                            }
                        }
                    }
                }

                // Error Banners overlay
                errorState?.let { err ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        if (err == "API_KEY_MISSING") {
                            MissingApiKeyBanner()
                        } else {
                            ErrorBanner(message = err)
                        }
                    }
                }
            }

            // Waiting / Loading / Voice Input States
            VoiceAndLoadingStatus(
                voiceState = voiceState,
                isLoading = isLoading
            )

            // Attached Files Row (Horizontal flow directly above the input row)
            AnimatedVisibility(
                visible = attachedFiles.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(attachedFiles) { file ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceCard)
                                .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = getFileIcon(file.iconType),
                                contentDescription = file.type,
                                tint = getFileColor(file.iconType),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = file.name,
                                color = TextLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove file",
                                tint = MutedText,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { viewModel.detachFile(file) }
                            )
                        }
                    }
                }
            }

            // Input Row
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = ObsidianBg,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ChatGPT input row capsule container
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(26.dp))
                            .background(SurfaceCard)
                            .border(1.dp, SlateBorder, RoundedCornerShape(26.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Plus button inside capsule
                        IconButton(
                            onClick = { showFilePicker = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SlateBorder)
                                .testTag("plus_add_files_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Files / Context",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Center text field
                        TextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            placeholder = {
                                Text(
                                    text = "Message Narrow AI...",
                                    color = MutedText,
                                    fontSize = 14.sp
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            singleLine = false,
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (textInput.isNotBlank()) {
                                        viewModel.sendMessage(textInput)
                                        textInput = ""
                                        keyboardController?.hide()
                                    }
                                }
                            )
                        )

                        // Right: Microphone icon inside capsule
                        val isListening = voiceState is VoiceState.Listening
                        IconButton(
                            onClick = {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    viewModel.toggleListening(context)
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isListening) Color(0xFF5A1C1C) else Color.Transparent)
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Vocal prompt",
                                tint = if (isListening) Color.Red else MutedText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // ChatGPT-like Blue Audio Conversation Button to the right of input capsule
                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank() && !isLoading) {
                                viewModel.sendMessage(textInput)
                                textInput = ""
                                keyboardController?.hide()
                            } else {
                                // Toggle speech mode / voice conversation directly!
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    viewModel.setVoiceModeEnabled(!isVoiceModeEnabled)
                                    viewModel.toggleListening(context)
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (textInput.isNotBlank()) NeonCyan else Color(0xFF2563EB)) // Custom premium Blue voice circle
                            .testTag("send_button")
                    ) {
                        if (textInput.isNotBlank()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = ObsidianBg,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            // High-fidelity vertical wave bar animation drawing
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(width = 3.dp, height = 12.dp).clip(CircleShape).background(Color.White))
                                Box(modifier = Modifier.size(width = 3.dp, height = 20.dp).clip(CircleShape).background(Color.White))
                                Box(modifier = Modifier.size(width = 3.dp, height = 15.dp).clip(CircleShape).background(Color.White))
                                Box(modifier = Modifier.size(width = 3.dp, height = 8.dp).clip(CircleShape).background(Color.White))
                            }
                        }
                    }
                }
            }
        }
    }

    // Attach File Selection Dialog
    if (showFilePicker) {
        AlertDialog(
            onDismissRequest = { showFilePicker = false },
            title = {
                Text(
                    text = "Add Context",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Attach real media or files from your device to share with Narrow AI:",
                        color = MutedText,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Option 1: Take Photo from Camera
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                showFilePicker = false
                                val hasCameraPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasCameraPermission) {
                                    cameraLauncher.launch(null)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SlateBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Camera Icon",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Take photo from camera",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Capture an image instantly and analyze it.",
                                color = MutedText,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Option 2: Files from Device
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                showFilePicker = false
                                fileLauncher.launch("*/*")
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SlateBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Files Icon",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Files from the device",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Attach PDF, text, code, csv or raw image files.",
                                color = MutedText,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilePicker = false }) {
                    Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun VoiceAndLoadingStatus(
    voiceState: VoiceState,
    isLoading: Boolean
) {
    AnimatedVisibility(
        visible = voiceState !is VoiceState.Idle || isLoading,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                voiceState is VoiceState.Listening -> {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 0.8f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                            .background(Color.Red, CircleShape)
                    )
                    Text(
                        text = "Listening to your voice... speak now",
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                voiceState is VoiceState.Speaking -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Speaking aloud",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Vocalizing answer...",
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                isLoading -> {
                    TypingIndicator()
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    onSpeak: (String) -> Unit,
    onCopy: () -> Unit
) {
    val isUser = message.role == "user"
    val align = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        if (isUser) {
            // User Message in dark gray bubble on the right
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceCard)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.message,
                    color = TextLight,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        } else {
            // Assistant Message directly in raw text (No bubble), matching ChatGPT style
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 32.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Narrow AI Logo",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = message.mode,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = message.message,
                    color = TextLight,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Elegant Gray Utility Buttons below Response (ChatGPT style copy, speech, share, thumb actions)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    var thumbUpSelected by remember { mutableStateOf(false) }
                    var thumbDownSelected by remember { mutableStateOf(false) }

                    // Copy Action
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy Response",
                        tint = MutedText,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onCopy() }
                    )

                    // Speak Action
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = "Speak Response",
                        tint = MutedText,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onSpeak(message.message) }
                    )

                    // Thumbs Up
                    Icon(
                        imageVector = if (thumbUpSelected) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Thumbs Up",
                        tint = if (thumbUpSelected) Color.White else MutedText,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable {
                                thumbUpSelected = !thumbUpSelected
                                if (thumbUpSelected) thumbDownSelected = false
                            }
                    )

                    // Thumbs Down
                    Icon(
                        imageVector = if (thumbDownSelected) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Thumbs Down",
                        tint = if (thumbDownSelected) Color.White else MutedText,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable {
                                thumbDownSelected = !thumbDownSelected
                                if (thumbDownSelected) thumbUpSelected = false
                            }
                    )

                    // Share Action
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Response",
                        tint = MutedText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ModeSelector(
    selectedMode: NarrowMode,
    onModeSelected: (NarrowMode) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianBg)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(NarrowMode.values()) { mode ->
            val isSelected = selectedMode == mode
            val activeColor = if (isSelected) ObsidianBg else TextLight
            val containerColor = if (isSelected) NeonCyan else SurfaceCard

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(containerColor)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) NeonCyan else SlateBorder,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = getModeIcon(mode),
                    contentDescription = mode.title,
                    tint = activeColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = mode.title,
                    color = activeColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun OnboardingEmptyState(
    mode: NarrowMode,
    onSuggestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Massive aesthetic centered sparkle logo
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Narrow AI Portal Logo",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "How can I help you today?",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Domain: ${mode.title}",
            color = MutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Suggestions block
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            mode.suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
                        .clickable { onSuggestionClick(suggestion) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = "Run prompt",
                        tint = MutedText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = suggestion,
                        color = TextLight,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.2f at 0
                1f at 300
                0.2f at 600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.2f at 150
                1f at 450
                0.2f at 750
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.2f at 300
                1f at 600
                0.2f at 900
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Narrow AI is writing",
            color = MutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer(alpha = alpha1)
                    .background(Color.White, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer(alpha = alpha2)
                    .background(Color.White, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer(alpha = alpha3)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

@Composable
fun MissingApiKeyBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1212)),
        border = BorderStroke(1.dp, Color(0xFF3F2222))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Gemini API Key Required",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Text(
                text = "Secure API key is missing. Please locate the 'Secrets' panel in your Google AI Studio UI and enter a valid GEMINI_API_KEY. Once saved, compile the applet to trigger the secure connection.",
                color = MutedText,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ErrorBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Error Notice",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                color = TextLight,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

fun getModeIcon(mode: NarrowMode): ImageVector {
    return when (mode) {
        NarrowMode.NARROW_AI -> Icons.Default.AutoAwesome
    }
}

fun getFileIcon(type: String): ImageVector {
    return when (type) {
        "pdf" -> Icons.Default.Description
        "csv" -> Icons.Default.BarChart
        "code" -> Icons.Default.Code
        "text" -> Icons.Default.Assignment
        "image" -> Icons.Default.Image
        else -> Icons.Default.AttachFile
    }
}

fun getFileColor(type: String): Color {
    return when (type) {
        "pdf" -> Color(0xFFEF4444) // Bright Red
        "csv" -> Color(0xFF10B981) // Bright Green
        "code" -> Color(0xFFF59E0B) // Golden Orange
        "text" -> Color(0xFF3B82F6) // Clear Blue
        "image" -> Color(0xFF06B6D4) // Teal Cyan
        else -> Color.White
    }
}
