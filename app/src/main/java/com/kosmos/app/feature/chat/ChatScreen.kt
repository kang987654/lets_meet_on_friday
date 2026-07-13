package com.kosmos.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.feature.voice.VoiceOverlay
import com.kosmos.app.ui.theme.Hairline
import com.kosmos.app.ui.theme.Ink
import com.kosmos.app.ui.theme.MutedText
import com.kosmos.app.ui.theme.SkyBlue
import com.kosmos.app.ui.theme.SkyBlueSoft
import com.kosmos.app.ui.theme.SurfaceCard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kosmos.app.platform.share.SharedInput
import com.kosmos.app.domain.modelrunner.ModelLoadState
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showVoiceOverlay by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size, uiState.isInFlight) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size) // scroll past last item for indicator
        }
    }

    LaunchedEffect(Unit) {
        viewModel.warmUpEngine()
    }

    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val mimeType = contentResolver.getType(uri)
                if (mimeType?.startsWith("image/") == true) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        val sharedImage = SharedInput.Image(
                            uri = uri,
                            sizeBytes = bytes.size.toLong()
                        )
                        viewModel.setSharedInput(sharedImage)
                    }
                } else {
                    // Try to read as text document
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val textContent = inputStream.bufferedReader().use { it.readText() }
                        // Get file name
                        var fileName = "document.txt"
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && nameIndex != -1) {
                                fileName = cursor.getString(nameIndex)
                            }
                        }
                        val sharedDoc = SharedInput.Document(
                            uri = uri,
                            fileName = fileName,
                            textContent = textContent.take(2500) // Limit text to prevent exceeding token bounds
                        )
                        viewModel.setSharedInput(sharedDoc)
                    }
                }
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleRecording()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local Friday", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                isLoading = uiState.isInFlight,
                isRecording = uiState.isRecording,
                sharedInput = uiState.sharedInput,
                onClearSharedInput = { viewModel.clearSharedInput() },
                onSend = { text ->
                    if (text.isNotBlank()) {
                        viewModel.sendMessage(text)
                    }
                },
                onMicClick = { 
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.toggleRecording()
                    } else {
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onAttachClick = { imagePickerLauncher.launch("*/*") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.engineState == ModelLoadState.InitializingEngine) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = SkyBlue
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "AI 엔진 초기화 중... 잠시만 기다려 주세요.",
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            if (uiState.warningMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color(0xFFFFF3E0)) // Soft Orange
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = uiState.warningMessage!!,
                        color = androidx.compose.ui.graphics.Color(0xFFE65100), // Dark Orange
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.messages) { message ->
                    if (message.role == ChatMessage.Role.USER) {
                        ChatBubbleUser(text = message.content, inputType = message.inputType)
                    } else {
                        ChatBubbleAssistant(text = message.content)
                    }
                }
                
                if (uiState.streamingText != null) {
                    item {
                        ChatBubbleAssistant(text = uiState.streamingText!!)
                    }
                }

                if (uiState.isInFlight && uiState.streamingText == null) {
                    item {
                        TypingIndicator()
                    }
                }
            } // end LazyColumn
        } // end Column
    } // end Scaffold

    if (uiState.pendingApproval != null) {
        val request = uiState.pendingApproval!!
        if (request.action is com.kosmos.app.domain.model.ModelOutput.SearchOutput) {
            val query = request.action.query
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.rejectPendingRequest() },
                title = { Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.web_search_approval_title), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                text = { 
                    Column {
                        Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.web_search_approval_message))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("'$query'", color = SkyBlue, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.web_search_approval_warning), color = androidx.compose.ui.graphics.Color.Red, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { viewModel.approvePendingRequest() }) {
                        Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.web_search_approve_once))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { viewModel.rejectPendingRequest() }) {
                        Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.web_search_reject))
                    }
                }
            )
        }
    }

    // VoiceOverlay placeholder removed
}

@Composable
fun ChatBubbleUser(text: String, inputType: com.kosmos.app.domain.model.InputType = com.kosmos.app.domain.model.InputType.TEXT) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(SkyBlueSoft, shape = RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                if (inputType == com.kosmos.app.domain.model.InputType.IMAGE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🖼️", modifier = Modifier.padding(end = 4.dp))
                        Text(
                            text = "첨부된 이미지",
                            color = MutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                } else if (inputType == com.kosmos.app.domain.model.InputType.VOICE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎤", modifier = Modifier.padding(end = 4.dp))
                        Text(
                            text = "음성 메시지",
                            color = MutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                }
                Text(
                    text = text,
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun ChatBubbleAssistant(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(SurfaceCard, shape = RoundedCornerShape(18.dp))
                .border(1.dp, Hairline, shape = RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            CompositionLocalProvider {
                ProvideTextStyle(
                    value = MaterialTheme.typography.bodyMedium.copy(color = Ink)
                ) {
                    RichText {
                        Markdown(content = text)
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(SurfaceCard, shape = RoundedCornerShape(18.dp))
                .border(1.dp, Hairline, shape = RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = SkyBlue,
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
fun ChatInputBar(
    isLoading: Boolean,
    isRecording: Boolean,
    sharedInput: com.kosmos.app.platform.share.SharedInput?,
    onClearSharedInput: () -> Unit,
    onSend: (String) -> Unit,
    onMicClick: () -> Unit,
    onAttachClick: () -> Unit
) {
    var textState by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(sharedInput) {
        if (sharedInput is com.kosmos.app.platform.share.SharedInput.Text) {
            textState = TextFieldValue(sharedInput.content)
            onClearSharedInput() // Consume the shared input text
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (sharedInput is com.kosmos.app.platform.share.SharedInput.Image) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(SurfaceCard, shape = RoundedCornerShape(12.dp))
                    .border(1.dp, Hairline, shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.image_attached), color = SkyBlue, style = MaterialTheme.typography.bodyMedium)
                        Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.image_size_kb, sharedInput.sizeBytes / 1024), color = MutedText, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onClearSharedInput() }) {
                        Text("X", color = MutedText)
                    }
                }
            }
        } else if (sharedInput is com.kosmos.app.platform.share.SharedInput.Document) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(SurfaceCard, shape = RoundedCornerShape(12.dp))
                    .border(1.dp, Hairline, shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Document Attached", color = SkyBlue, style = MaterialTheme.typography.bodyMedium)
                        Text(sharedInput.fileName, color = MutedText, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onClearSharedInput() }) {
                        Text("X", color = MutedText)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = { onAttachClick() },
                enabled = !isLoading,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(48.dp)
                    .background(SurfaceCard, shape = RoundedCornerShape(24.dp))
                    .border(1.dp, Hairline, shape = RoundedCornerShape(24.dp))
            ) {
                Text(
                    text = "+", 
                    color = if (!isLoading) SkyBlue else MutedText,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceCard, shape = RoundedCornerShape(24.dp))
                    .border(1.dp, Hairline, shape = RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (textState.text.isEmpty()) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.chat_input_hint),
                        color = MutedText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                BasicTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                    cursorBrush = SolidColor(SkyBlue),
                    enabled = !isLoading,
                    maxLines = 5
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            if (textState.text.isNotBlank()) {
                IconButton(
                    onClick = {
                        onSend(textState.text)
                        textState = TextFieldValue("")
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (!isLoading) SkyBlue else Hairline, shape = RoundedCornerShape(24.dp))
                ) {
                    Text(
                        text = "↑", 
                        color = if (!isLoading) SurfaceCard else MutedText,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                IconButton(
                    onClick = { onMicClick() },
                    enabled = !isLoading,
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (!isLoading) (if (isRecording) androidx.compose.ui.graphics.Color.Red else Ink) else Hairline, shape = RoundedCornerShape(24.dp))
                ) {
                    Text(
                        text = if (isRecording) "■" else "M", // Mic/Stop placeholder
                        color = if (!isLoading) SurfaceCard else MutedText,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
