package com.kosmos.app.feature.settings

import com.kosmos.app.ui.theme.KosmosTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.platform.LocalContext
import com.kosmos.app.ui.component.glassEffect
import com.kosmos.app.domain.modelrunner.ModelLoadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    themeViewModel: com.kosmos.app.ui.theme.ThemeViewModel = hiltViewModel(),
    onNavigateToModelManagement: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "설정",
            style = MaterialTheme.typography.headlineMedium,
            color = KosmosTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 1. Model Status Section
        SectionBox(title = "로컬 AI 모델 (GEMMA)") {
            when (val state = uiState.modelLoadState) {
                is ModelLoadState.Loading -> {
                    CircularProgressIndicator(color = KosmosTheme.colors.accent)
                    Text("모델 상태 확인 중…", color = KosmosTheme.colors.textMuted, modifier = Modifier.padding(top = 8.dp))
                }
                is ModelLoadState.FileFound -> {
                    CircularProgressIndicator(color = KosmosTheme.colors.accent)
                    Text("모델 파일 확인, 엔진 준비 중…", color = KosmosTheme.colors.textMuted, modifier = Modifier.padding(top = 8.dp))
                }
                is ModelLoadState.InitializingEngine -> {
                    CircularProgressIndicator(color = KosmosTheme.colors.accent)
                    Text("AI 엔진 초기화 중…", color = KosmosTheme.colors.textMuted, modifier = Modifier.padding(top = 8.dp))
                }
                is ModelLoadState.Ready -> {
                    Text(
                        text = "상태: 준비됨",
                        color = KosmosTheme.colors.success,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("모델: ${state.modelInfo.modelId}", color = KosmosTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("버전: ${state.modelInfo.modelVersion}", color = KosmosTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("양자화: ${state.modelInfo.quantization}", color = KosmosTheme.colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "경로: ${state.modelInfo.modelPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = KosmosTheme.colors.textMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassEffect(
                                backgroundColor = KosmosTheme.colors.accent.copy(alpha = 0.15f),
                                borderColor = KosmosTheme.colors.accent.copy(alpha = 0.3f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                            .clickable { onNavigateToModelManagement() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("모델 관리", color = KosmosTheme.colors.accent, fontWeight = FontWeight.Bold)
                    }
                }
                is ModelLoadState.NotFound -> {
                    Text(
                        text = "상태: 모델 없음",
                        color = KosmosTheme.colors.danger,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "모델 파일을 찾을 수 없어요. 내려받으면 바로 사용할 수 있어요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KosmosTheme.colors.danger
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .glassEffect(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .clickable { viewModel.refreshModelState() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("새로 고침", color = KosmosTheme.colors.textPrimary)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .glassEffect(
                                    backgroundColor = KosmosTheme.colors.accent.copy(alpha = 0.2f),
                                    borderColor = KosmosTheme.colors.accent.copy(alpha = 0.5f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                )
                                .clickable { onNavigateToModelManagement() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("내려받기", color = KosmosTheme.colors.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is ModelLoadState.Error -> {
                    Text(
                        text = "상태: 오류",
                        color = KosmosTheme.colors.danger,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = com.kosmos.app.core.mapper.ErrorMessages.userMessage(state.error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = KosmosTheme.colors.danger,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // 2. Appearance Section (ADR-005: 라이트/다크 테마 전환)
        SectionBox(title = "화면 테마") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                com.kosmos.app.ui.theme.ThemeMode.entries.forEach { mode ->
                    val isSelected = themeMode == mode
                    val bgColor = if (isSelected) KosmosTheme.colors.accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent
                    val textColor = if (isSelected) KosmosTheme.colors.accent else KosmosTheme.colors.textMuted

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(bgColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                            .clickable { themeViewModel.setThemeMode(mode) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            color = textColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // 3. Response Style Section
        SectionBox(title = "응답 스타일") {
            // 저장값(영문 키)은 그대로 두고 표시만 한글화한다 — 키를 바꾸면 기존 설정이 깨진다.
            val styles = listOf("CONCISE" to "간결", "DEFAULT" to "기본", "DETAILED" to "자세히")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                styles.forEach { (style, label) ->
                    val isSelected = uiState.responseStyle == style
                    val bgColor = if (isSelected) KosmosTheme.colors.accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent
                    val textColor = if (isSelected) KosmosTheme.colors.accent else KosmosTheme.colors.textMuted

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(bgColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                            .clickable { viewModel.onResponseStyleChanged(style) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = textColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        
        // 3. Prefill Budget Section
        // [WHY] 예전 제목은 "CONTEXT WINDOW / Token Limit" 이었다. 이 값은 모델의 컨텍스트
        // 윈도우가 아니다 — 우리가 매 턴 모델에게 실어 보내는 양(히스토리 예산)과 대화를 언제
        // 재설정할지를 정한다. 이름이 실제 동작과 다르면 사용자는 만져도 아무 효과가 없다고
        // 느끼게 된다.
        //
        // [WHY] 상한이 8000 이었다. 그런데 엔진 KV 는 4096 이므로 **슬라이더를 올릴 수 있는
        // 범위의 절반 이상이 애초에 담기지 않는 값**이었다 — 사용자가 8000 으로 올리면 조용히
        // 초과되어 품질이 떨어졌다. 실제로 담을 수 있는 천장까지만 노출한다
        // (근거: `Constants.ENGINE_MAX_TOKENS`).
        SectionBox(title = "대화 기억 범위") {
            Column {
                var sliderValue by remember(uiState.maxTokens) { mutableFloatStateOf(uiState.maxTokens.toFloat()) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("한 번에 참고할 분량", color = KosmosTheme.colors.textPrimary)
                    Text("${sliderValue.toInt()} 토큰", color = KosmosTheme.colors.accent, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { viewModel.onMaxTokensChanged(sliderValue.toInt()) },
                    // [WHY] 상한 1700 = GPU 숫자 깨짐 발병점(실측 1854~2122, exp30) 아래
                    // (근거: Constants.MAX_CONTEXT_TOKENS, ADR-021). 그 위 값은 긴 대화에서
                    // 일정 시각·비밀번호의 숫자를 깨뜨린다.
                    valueRange = 1000f..1700f,
                    steps = 6, // 100 단위: 1000..1700
                    colors = SliderDefaults.colors(
                        thumbColor = KosmosTheme.colors.accent,
                        activeTrackColor = KosmosTheme.colors.accent,
                        inactiveTrackColor = KosmosTheme.colors.glass
                    )
                )
                Text(
                    text = "값을 올리면 이전 대화를 더 많이 참고하지만 응답이 느려지고 메모리를 더 씁니다. " +
                        "모델이 한 번에 읽을 수 있는 전체 한도와는 다릅니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KosmosTheme.colors.textMuted
                )
            }
        }

        // 5. 아침 브리핑 (A4 — 알림은 미리보기, 본문은 앱을 열면 도착)
        SectionBox(title = "아침 브리핑") {
            var showTimePicker by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("매일 아침 브리핑 받기", color = KosmosTheme.colors.textPrimary)
                Switch(
                    checked = uiState.briefingEnabled,
                    onCheckedChange = { viewModel.onBriefingEnabledChanged(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = KosmosTheme.colors.onAccent,
                        checkedTrackColor = KosmosTheme.colors.accent
                    )
                )
            }
            if (uiState.briefingEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("알림 시각", color = KosmosTheme.colors.textPrimary)
                    Text(
                        text = "%02d:%02d".format(
                            uiState.briefingTimeMinutes / 60,
                            uiState.briefingTimeMinutes % 60
                        ),
                        color = KosmosTheme.colors.accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .glassEffect(shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .clickable { showTimePicker = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "정시에 일정·할 일 개수를 알려드리고, 앱을 열면 브리핑이 대화로 도착해요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KosmosTheme.colors.textMuted
                )
            }

            if (showTimePicker) {
                val timeState = rememberTimePickerState(
                    initialHour = uiState.briefingTimeMinutes / 60,
                    initialMinute = uiState.briefingTimeMinutes % 60,
                    is24Hour = true
                )
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    title = { Text("브리핑 시각") },
                    text = { TimePicker(state = timeState) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.onBriefingTimeChanged(timeState.hour * 60 + timeState.minute)
                            showTimePicker = false
                        }) { Text("확인", color = KosmosTheme.colors.accent) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("취소", color = KosmosTheme.colors.textMuted)
                        }
                    },
                    containerColor = KosmosTheme.colors.surface,
                    titleContentColor = KosmosTheme.colors.textPrimary
                )
            }
        }

        // [WHY] SECURITY & LOGS 섹션은 제거했다 — 활동 기록 진입점이 드로어 타일로 옮겨져
        // (M2-2) 같은 화면으로 가는 문이 두 개였다 (2026-08-15 사용자 피드백).
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun SectionBox(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(
                backgroundColor = KosmosTheme.colors.glass,
                borderColor = KosmosTheme.colors.borderHigh,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = KosmosTheme.colors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
                    fontWeight = FontWeight.Bold,
                    color = KosmosTheme.colors.textMuted,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}
