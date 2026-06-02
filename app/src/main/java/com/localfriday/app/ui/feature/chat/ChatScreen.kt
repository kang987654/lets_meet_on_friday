package com.localfriday.app.ui.feature.chat

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localfriday.app.domain.model.ChatMessage
import com.localfriday.app.ui.theme.Hairline
import com.localfriday.app.ui.theme.Ink
import com.localfriday.app.ui.theme.MutedText
import com.localfriday.app.ui.theme.SkyBlue
import com.localfriday.app.ui.theme.SkyBlueSoft
import com.localfriday.app.ui.theme.SurfaceCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size, uiState.isInFlight) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size) // scroll past last item for indicator
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
                onSend = { text ->
                    if (text.isNotBlank()) {
                        viewModel.sendMessage(text)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(uiState.messages) { message ->
                if (message.role == ChatMessage.Role.USER) {
                    ChatBubbleUser(text = message.content)
                } else {
                    ChatBubbleAssistant(text = message.content)
                }
            }

            if (uiState.isInFlight) {
                item {
                    TypingIndicator()
                }
            }
        }
    }
}

@Composable
fun ChatBubbleUser(text: String) {
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
            Text(
                text = text,
                color = Ink,
                style = MaterialTheme.typography.bodyMedium
            )
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
            Text(
                text = text,
                color = Ink,
                style = MaterialTheme.typography.bodyMedium
            )
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
    onSend: (String) -> Unit
) {
    var textState by remember { mutableStateOf(TextFieldValue("")) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(SurfaceCard, shape = RoundedCornerShape(24.dp))
                .border(1.dp, Hairline, shape = RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (textState.text.isEmpty()) {
                Text(
                    text = "메시지를 입력하세요...",
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
        
        IconButton(
            onClick = {
                onSend(textState.text)
                textState = TextFieldValue("")
            },
            enabled = textState.text.isNotBlank() && !isLoading,
            modifier = Modifier
                .size(48.dp)
                .background(if (textState.text.isNotBlank() && !isLoading) SkyBlue else Hairline, shape = RoundedCornerShape(24.dp))
        ) {
            // Note: In real app, we use an icon resource. We'll use text as placeholder if icon is not available, 
            // but Compose provides Icons.Default.Send. However, material-icons-extended might not be added.
            // Using a simple Text for now.
            Text(
                text = "↑", 
                color = if (textState.text.isNotBlank() && !isLoading) SurfaceCard else MutedText,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
