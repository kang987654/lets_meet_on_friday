package com.kosmos.app.feature.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosmos.app.R
import com.kosmos.app.core.common.ResponseStyle
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.platform.notification.NotificationAccess
import com.kosmos.app.ui.component.KosmosButton
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.theme.currentAppLanguage
import com.kosmos.app.ui.theme.scaled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    themeViewModel: com.kosmos.app.ui.theme.ThemeViewModel = hiltViewModel(),
    onNavigateToModelManagement: () -> Unit = {},
    onNavigateToMemoryCleanup: () -> Unit = {},
    dataResetViewModel: DataResetViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val languageOption by themeViewModel.languageOption.collectAsStateWithLifecycle()
    val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val fontMode by themeViewModel.fontMode.collectAsStateWithLifecycle()
    val chatTextSize by themeViewModel.chatTextSize.collectAsStateWithLifecycle()
    val splashStyle by themeViewModel.splashStyle.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val lang = currentAppLanguage()

    Column(modifier = Modifier.fillMaxSize()) {
        com.kosmos.app.ui.component.KosmosTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        // [WHY] 섹션마다 큰 상자 + 톱니 아이콘 + 자간 넓힌 제목(eyebrow)이었다 — 섹션 제목 + 목록 묶음 + 각주로(0.38.3 M3 시안 A, impeccable "설정은 묶음 목록").
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Section(stringResource(R.string.settings_section_model)) { ModelStatus(uiState.modelLoadState, onNavigateToModelManagement, viewModel::refreshModelState) }

            // [WHY] 고르는 값은 세그먼트로 바로 바꾼다(시안 A, 사용자 선택) — 값 행 + 시트는 한 번 더 눌러야 하고 글자 크기 미리보기가 시트로 숨는다.
            Section(stringResource(R.string.settings_section_display), footnote = stringResource(R.string.settings_display_footnote)) {
                Column(Modifier.padding(16.dp)) {
                    // [WHY] LocaleManager 는 Android 13(API 33)+ 전용이라 리소스 기반 전환이 동작하지 않는 API 32 이하에서는 숨긴다(0.40.1 결정).
                    val showLanguageToggle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    if (showLanguageToggle) {
                        ControlLabel(stringResource(R.string.settings_control_language))
                        com.kosmos.app.ui.component.KosmosSegmentedControl(
                            options = listOf(
                                com.kosmos.app.core.common.AppLanguage.Option.SYSTEM to stringResource(R.string.lang_option_system),
                                com.kosmos.app.core.common.AppLanguage.Option.KO to stringResource(R.string.lang_option_ko),
                                com.kosmos.app.core.common.AppLanguage.Option.EN to stringResource(R.string.lang_option_en)
                            ),
                            selected = languageOption,
                            onSelect = themeViewModel::setLanguageOption
                        )
                    }
                    ControlLabel(stringResource(R.string.settings_control_theme), top = if (showLanguageToggle) 16.dp else 0.dp)
                    com.kosmos.app.ui.component.KosmosSegmentedControl(
                        options = com.kosmos.app.ui.theme.ThemeMode.entries.map { mode ->
                            val label = when (mode) {
                                com.kosmos.app.ui.theme.ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
                                com.kosmos.app.ui.theme.ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
                                com.kosmos.app.ui.theme.ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
                            }
                            mode to label
                        },
                        selected = themeMode,
                        onSelect = themeViewModel::setThemeMode
                    )
                    // [WHY] 하다 라운드가 기본이지만 기기 글꼴을 쓰는 사람도 있다 — 크기·굵기 단계는 같고 글꼴만 바뀐다(DESIGN.md v3 Typography).
                    ControlLabel(stringResource(R.string.settings_control_font), top = 16.dp)
                    com.kosmos.app.ui.component.KosmosSegmentedControl(
                        options = com.kosmos.app.ui.theme.FontMode.entries.map { mode ->
                            val label = when (mode) {
                                com.kosmos.app.ui.theme.FontMode.APP -> stringResource(R.string.font_mode_app)
                                com.kosmos.app.ui.theme.FontMode.SYSTEM -> stringResource(R.string.font_mode_system)
                            }
                            mode to label
                        },
                        selected = fontMode,
                        onSelect = themeViewModel::setFontMode
                    )
                    // [WHY] 채팅 메시지만 키운다(0.38.2 사용자 결정) — 화면 틀은 시스템 글자 크기를 따른다.
                    ControlLabel(stringResource(R.string.settings_control_chat_text_size), top = 16.dp)
                    com.kosmos.app.ui.component.KosmosSegmentedControl(
                        options = com.kosmos.app.ui.theme.ChatTextSize.entries.map { size ->
                            val label = when (size) {
                                com.kosmos.app.ui.theme.ChatTextSize.SMALL -> stringResource(R.string.chat_text_size_small)
                                com.kosmos.app.ui.theme.ChatTextSize.DEFAULT -> stringResource(R.string.chat_text_size_default)
                                com.kosmos.app.ui.theme.ChatTextSize.LARGE -> stringResource(R.string.chat_text_size_large)
                                com.kosmos.app.ui.theme.ChatTextSize.XLARGE -> stringResource(R.string.chat_text_size_xlarge)
                            }
                            size to label
                        },
                        selected = chatTextSize,
                        onSelect = themeViewModel::setChatTextSize
                    )
                    Text(
                        text = stringResource(R.string.settings_chat_text_preview),
                        // 채팅 목록과 같은 배율 — 고른 크기를 그대로 미리 본다.
                        style = MaterialTheme.typography.scaled(chatTextSize.scale).bodyLarge,
                        color = KosmosTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    // [WHY] 두 장면을 모두 확정했다(0.38.4 M6 사용자 결정) — 다음에 앱을 열 때부터 바뀐다.
                    ControlLabel(stringResource(R.string.settings_control_splash), top = 16.dp)
                    com.kosmos.app.ui.component.KosmosSegmentedControl(
                        options = com.kosmos.app.ui.theme.SplashStyle.entries.map { style ->
                            val label = when (style) {
                                com.kosmos.app.ui.theme.SplashStyle.PLANET -> stringResource(R.string.splash_style_planet)
                                com.kosmos.app.ui.theme.SplashStyle.GALAXY -> stringResource(R.string.splash_style_galaxy)
                            }
                            style to label
                        },
                        selected = splashStyle ?: com.kosmos.app.ui.theme.SplashStyle.PLANET,
                        onSelect = themeViewModel::setSplashStyle
                    )
                }
            }

            Section(
                stringResource(R.string.settings_section_conversation),
                footnote = stringResource(R.string.settings_conversation_footnote)
            ) {
                Column(Modifier.padding(16.dp)) {
                    ControlLabel(stringResource(R.string.settings_control_response_style))
                    // 저장값(영문 키)은 그대로 두고 표시만 한글화한다 — 키를 바꾸면 기존 설정이 깨진다.
                    val styles = listOf(
                        ResponseStyle.CONCISE to stringResource(R.string.response_style_concise),
                        ResponseStyle.DEFAULT to stringResource(R.string.response_style_default),
                        ResponseStyle.DETAILED to stringResource(R.string.response_style_detailed)
                    )
                    com.kosmos.app.ui.component.KosmosSegmentedControl(
                        options = styles,
                        selected = uiState.responseStyle,
                        onSelect = viewModel::onResponseStyleChanged
                    )
                }
                HorizontalDivider(color = KosmosTheme.colors.border)
                // [WHY] 이 값은 모델의 컨텍스트 윈도우가 아니라 매 턴 싣는 히스토리 예산이다 — 이름이 동작과 다르면 만져도 효과가 없다고 느낀다.
                // 범위는 엔진이 실제로 담을 수 있는 천장까지만(`Constants.MIN_CONTEXT_TOKENS`·`MAX_CONTEXT_TOKENS`, ADR-029).
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                    var sliderValue by remember(uiState.maxTokens) { mutableFloatStateOf(uiState.maxTokens.toFloat()) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.settings_control_context_budget), style = MaterialTheme.typography.bodyLarge, color = KosmosTheme.colors.textPrimary)
                        Text("%,d".format(sliderValue.toInt()), style = MaterialTheme.typography.bodyMedium, color = KosmosTheme.colors.textSecondary)
                    }
                    val sliderColors = SliderDefaults.colors(
                        thumbColor = KosmosTheme.colors.brand,
                        activeTrackColor = KosmosTheme.colors.brand,
                        inactiveTrackColor = KosmosTheme.colors.surfaceSunk,
                        // [WHY] 100 단위 눈금 수십 개와 끝 표시 점이 점선처럼 보인다 — 값은 오른쪽 숫자가 알려 준다.
                        activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                        inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { viewModel.onMaxTokensChanged(sliderValue.toInt()) },
                        valueRange = com.kosmos.app.core.common.Constants.MIN_CONTEXT_TOKENS.toFloat()..com.kosmos.app.core.common.Constants.MAX_CONTEXT_TOKENS.toFloat(),
                        steps = (com.kosmos.app.core.common.Constants.MAX_CONTEXT_TOKENS - com.kosmos.app.core.common.Constants.MIN_CONTEXT_TOKENS) / 100 - 1, // 100 단위
                        colors = sliderColors,
                        track = { state -> SliderDefaults.Track(sliderState = state, colors = sliderColors, drawStopIndicator = null) }
                    )
                }
            }

            // 아침 브리핑 (A4 — 알림은 미리보기, 본문은 앱을 열면 도착)
            var showTimePicker by remember { mutableStateOf(false) }
            val briefingContext = LocalContext.current
            var notificationDenied by remember { mutableStateOf(false) }
            // [WHY] 브리핑은 알림으로 도착을 알린다 — 켜는 순간 알림 권한을 요청한다(0.36.0 D4). 거부해도 켜 두고, 카드는 앱을 열면 보인다.
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> notificationDenied = !granted }
            val briefingWarning = if (uiState.briefingEnabled && (notificationDenied || !NotificationAccess.canPost(briefingContext))) NotificationAccess.deniedMessage(lang) else null
            Section(
                stringResource(R.string.settings_section_briefing),
                footnote = if (uiState.briefingEnabled) stringResource(R.string.settings_briefing_footnote) else null,
                warning = briefingWarning
            ) {
                SettingSwitchRow(stringResource(R.string.settings_briefing_switch), uiState.briefingEnabled, divider = uiState.briefingEnabled) { enabled ->
                    viewModel.onBriefingEnabledChanged(enabled)
                    val permission = NotificationAccess.runtimePermission
                    if (enabled && permission != null && !NotificationAccess.canPost(briefingContext)) {
                        notificationPermissionLauncher.launch(permission)
                    }
                }
                if (uiState.briefingEnabled) {
                    com.kosmos.app.ui.component.KosmosListRow(
                        title = stringResource(R.string.settings_briefing_time_row),
                        onClick = { showTimePicker = true },
                        trailing = { ValueTrailing(com.kosmos.app.domain.util.IsoDateTimeParser.clockKorean(uiState.briefingTimeMinutes, lang)) }
                    )
                }
            }

            if (showTimePicker) {
                val timeState = rememberTimePickerState(
                    initialHour = uiState.briefingTimeMinutes / 60,
                    initialMinute = uiState.briefingTimeMinutes % 60,
                    is24Hour = false
                )
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    title = { Text(stringResource(R.string.settings_briefing_time_dialog_title)) },
                    text = { TimePicker(state = timeState) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.onBriefingTimeChanged(timeState.hour * 60 + timeState.minute)
                            showTimePicker = false
                        }) { Text(stringResource(R.string.common_confirm), color = KosmosTheme.colors.brandText) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(R.string.common_cancel), color = KosmosTheme.colors.textMuted)
                        }
                    },
                    containerColor = KosmosTheme.colors.surface,
                    titleContentColor = KosmosTheme.colors.textPrimary
                )
            }

            // 자동 기억 (C′2) — 대화가 정리될 때 기억할 사실을 골라 저장
            Section(
                stringResource(R.string.settings_section_memory),
                footnote = stringResource(R.string.settings_memory_footnote)
            ) {
                SettingSwitchRow(stringResource(R.string.settings_memory_auto_extract), uiState.autoExtractEnabled, divider = true) { viewModel.onAutoExtractEnabledChanged(it) }
                // [WHY] 수동 정리(0.30.0) — 야간 배치 대신 사용자가 누를 때만 돈다(백그라운드 모델 로드 금지, §2-⑥).
                com.kosmos.app.ui.component.KosmosListRow(
                    title = stringResource(R.string.settings_memory_cleanup_title),
                    supporting = stringResource(R.string.settings_memory_cleanup_sub),
                    onClick = onNavigateToMemoryCleanup,
                    trailing = { Chevron() }
                )
            }

            // 음성 출력 (0.29.0, A2) — 내장 TTS 엔진으로 답변 낭독
            val ttsEngines by viewModel.ttsEngines.collectAsStateWithLifecycle()
            LaunchedEffect(lang) { viewModel.loadTtsEngines() }
            val ttsContext = LocalContext.current
            val voiceNotice = when (uiState.voiceStatus) {
                com.kosmos.app.platform.speech.SpeechOutput.VoiceStatus.NO_KOREAN_OFFLINE_VOICE ->
                    stringResource(R.string.settings_voice_no_offline_warning)
                com.kosmos.app.platform.speech.SpeechOutput.VoiceStatus.INIT_FAILED ->
                    stringResource(R.string.settings_voice_init_failed_warning)
                else -> null
            }
            Section(
                stringResource(R.string.settings_section_voice),
                footnote = stringResource(R.string.settings_voice_footnote),
                warning = voiceNotice
            ) {
                SettingSwitchRow(stringResource(R.string.settings_voice_auto_read), uiState.ttsAutoRead, divider = true) { viewModel.onTtsAutoReadChanged(it) }
                // [WHY] "시스템 기본" + 설치된 엔진(삼성·구글 등) — 목록이 비면 매니페스트 TTS_SERVICE queries 누락을 의심한다.
                val engines = listOf(com.kosmos.app.platform.speech.SpeechOutput.Engine("", stringResource(R.string.settings_voice_engine_default))) + ttsEngines
                engines.forEachIndexed { index, engine ->
                    com.kosmos.app.ui.component.KosmosListRow(
                        title = engine.label,
                        modifier = Modifier.selectable(
                            selected = uiState.ttsEngine == engine.packageName,
                            role = androidx.compose.ui.semantics.Role.RadioButton,
                            onClick = { viewModel.onTtsEngineChanged(engine.packageName) }
                        ),
                        divider = index < engines.lastIndex,
                        trailing = {
                            RadioButton(
                                selected = uiState.ttsEngine == engine.packageName,
                                onClick = null,
                                // 행이 누르는 영역이라 버튼의 48dp 여백이 빠진다 — 예전 자리(끝에서 28dp 가운데)에 맞춘다.
                                modifier = Modifier.padding(end = 14.dp),
                                colors = RadioButtonDefaults.colors(selectedColor = KosmosTheme.colors.brand)
                            )
                        }
                    )
                }
            }
            if (uiState.voiceStatus == com.kosmos.app.platform.speech.SpeechOutput.VoiceStatus.NO_KOREAN_OFFLINE_VOICE) {
                TextButton(onClick = {
                    val intent = android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                        .apply { if (uiState.ttsEngine.isNotBlank()) setPackage(uiState.ttsEngine) }
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    // [WHY] 엔진이 설치 화면을 제공하지 않을 수 있다 — 실패는 조용히 무시(안내 문구는 이미 보이고 있다).
                    runCatching { ttsContext.startActivity(intent) }
                }) { Text(stringResource(R.string.settings_voice_install_data), color = KosmosTheme.colors.brandText) }
            }

            // 6. 리마인더 정확 알람 안내 (B1) — 권한이 없을 때만 노출
            val context = LocalContext.current
            var exactAlarmAllowed by remember { mutableStateOf(true) }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            // [WHY] canScheduleExactAlarms 에는 Flow 가 없다 — 시스템 설정에서 돌아오는
            // 순간(ON_RESUME)에 재확인해야 허용 직후 이 안내가 사라진다. SettingsViewModel 의
            // combine 은 5개 상한에 닿아 있으므로(0.20.0 경계 기록) 시스템 API 상태는 화면에서
            // 직접 읽는다 — DataStore 상태가 아니라 combine 에 넣을 이유도 없다.
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        exactAlarmAllowed =
                            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                            context.getSystemService(android.app.AlarmManager::class.java)
                                ?.canScheduleExactAlarms() != false
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            if (!exactAlarmAllowed) {
                Section(
                    stringResource(R.string.settings_section_exact_alarm),
                    warning = stringResource(R.string.settings_exact_alarm_warning)
                ) {
                    com.kosmos.app.ui.component.KosmosListRow(
                        title = stringResource(R.string.settings_exact_alarm_allow),
                        onClick = {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                                ).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                            )
                        },
                        trailing = { Chevron() }
                    )
                }
            }

            val resetState by dataResetViewModel.state.collectAsStateWithLifecycle()
            DataResetSection(
                state = resetState,
                onReset = dataResetViewModel::reset,
                onDismiss = dataResetViewModel::dismiss,
                onRestart = { com.kosmos.app.ui.restartApp(context) }
            )

            // [WHY] SECURITY & LOGS 섹션은 제거했다 — 활동 기록 진입점이 드로어 타일로 옮겨져
            // (M2-2) 같은 화면으로 가는 문이 두 개였다 (2026-08-15 사용자 피드백).
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

/**
 * 설정 한 섹션 — 섹션 제목 + 목록 묶음 + 묶음 아래 각주(Apple·Intercom 설정 문법, 0.38.3 M3).
 *
 * @param warning 각주 자리의 경고(권한 거부 등) — 각주보다 먼저, 경고색으로.
 */
@Composable
private fun Section(
    title: String,
    footnote: String? = null,
    warning: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    com.kosmos.app.ui.component.SectionHeader(title, Modifier.padding(top = 16.dp))
    com.kosmos.app.ui.component.KosmosListGroup(content = content)
    warning?.let { Footnote(it, KosmosTheme.colors.warningText) }
    footnote?.let { Footnote(it, KosmosTheme.colors.textMuted) }
}

@Composable
private fun Footnote(text: String, color: androidx.compose.ui.graphics.Color) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))

/** 묶음 안 세그먼트 위의 작은 이름표. */
@Composable
private fun ControlLabel(text: String, top: androidx.compose.ui.unit.Dp = 0.dp) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = KosmosTheme.colors.textSecondary, modifier = Modifier.padding(top = top, bottom = 8.dp))

@Composable
private fun Chevron() = com.kosmos.app.ui.component.KosmosIcon(
    R.drawable.ic_ph_caret_right, contentDescription = null,
    size = com.kosmos.app.ui.component.IconSize.inline, tint = KosmosTheme.colors.textMuted, modifier = Modifier.padding(end = 12.dp)
)

/** 값 행의 끝 — 현재 값 + 화살표. */
@Composable
private fun ValueTrailing(value: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(value, style = MaterialTheme.typography.bodyMedium, color = KosmosTheme.colors.textSecondary, modifier = Modifier.padding(end = 4.dp))
    Chevron()
}

/** 라벨 + 스위치 한 줄 — 행 전체를 눌러도 바뀐다(누르는 영역). */
@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, divider: Boolean = false, onCheckedChange: (Boolean) -> Unit) {
    // [WHY] 행과 스위치가 따로 포커스를 잡아 화면 읽기가 두 번 읽었다 — 행 하나가 스위치 역할(0.38.3 점검 P3).
    com.kosmos.app.ui.component.KosmosListRow(
        title = label,
        modifier = Modifier.toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onCheckedChange),
        divider = divider,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 12.dp),
                // [WHY] 꺼짐은 손잡이·테두리를 textMuted 로(흰 묶음 위에서 비텍스트 대비 3:1), 켜짐 손잡이는 면 색(검은 손잡이가 너무 무거웠다) — 0.38.3 M6 점검 P1.
                colors = SwitchDefaults.colors(
                    checkedThumbColor = KosmosTheme.colors.surface,
                    checkedTrackColor = KosmosTheme.colors.brand,
                    checkedBorderColor = KosmosTheme.colors.brand,
                    uncheckedThumbColor = KosmosTheme.colors.textMuted,
                    uncheckedTrackColor = KosmosTheme.colors.surface,
                    uncheckedBorderColor = KosmosTheme.colors.textMuted
                )
            )
        }
    )
}

/**
 * 모델 상태 — 준비됨이면 모델 이름 행(누르면 모델 관리), 자세한 정보는 접어 둔다. 확인 중에도 내용 한가운데 스피너를 두지 않는다(impeccable "spinner in content").
 */
@Composable
private fun ModelStatus(state: ModelLoadState, onManage: () -> Unit, onRefresh: () -> Unit) {
    val lang = currentAppLanguage()
    when (state) {
        is ModelLoadState.Loading, is ModelLoadState.FileFound, is ModelLoadState.InitializingEngine -> com.kosmos.app.ui.component.KosmosListRow(
            title = stringResource(R.string.settings_model_local_title),
            supporting = when (state) {
                is ModelLoadState.Loading -> stringResource(R.string.settings_model_checking)
                is ModelLoadState.FileFound -> stringResource(R.string.settings_model_file_found)
                else -> stringResource(R.string.settings_model_preparing)
            },
            tags = listOf(stringResource(R.string.settings_model_tag_preparing))
        )
        is ModelLoadState.Ready -> {
            var detailsOpen by rememberSaveable { mutableStateOf(false) }
            com.kosmos.app.ui.component.KosmosListRow(
                title = com.kosmos.app.ui.modelDisplayName(state.modelInfo.modelId),
                tags = listOf(stringResource(R.string.settings_model_tag_ready)),
                onClick = onManage,
                divider = true,
                trailing = { Chevron() }
            )
            com.kosmos.app.ui.component.KosmosListRow(
                title = stringResource(R.string.settings_model_info_title),
                onClick = { detailsOpen = !detailsOpen },
                trailing = {
                    com.kosmos.app.ui.component.KosmosIcon(
                        if (detailsOpen) R.drawable.ic_ph_caret_up else R.drawable.ic_ph_caret_down,
                        contentDescription = if (detailsOpen) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                        size = com.kosmos.app.ui.component.IconSize.inline, tint = KosmosTheme.colors.textMuted, modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
            if (detailsOpen) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(
                        stringResource(R.string.settings_model_version, state.modelInfo.modelVersion),
                        stringResource(R.string.settings_model_quantization, state.modelInfo.quantization),
                        state.modelInfo.modelPath
                    ).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = KosmosTheme.colors.textSecondary)
                    }
                }
            }
        }
        is ModelLoadState.NotFound -> {
            com.kosmos.app.ui.component.KosmosListRow(
                title = stringResource(R.string.settings_model_not_found_title),
                supporting = stringResource(R.string.settings_model_not_found_body)
            )
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KosmosButton(stringResource(R.string.common_refresh), onRefresh, Modifier.weight(1f))
                KosmosButton(stringResource(R.string.common_download), onManage, Modifier.weight(1f), accent = true)
            }
        }
        is ModelLoadState.Error -> com.kosmos.app.ui.component.KosmosListRow(
            title = stringResource(R.string.settings_model_load_failed_title),
            supporting = com.kosmos.app.core.mapper.ErrorMessages.userMessage(state.error, lang),
            onClick = onManage,
            trailing = { Chevron() }
        )
    }
}

/** 초기화 대상 한 줄 — 화면 문구가 곧 [com.kosmos.app.domain.memory.ResetTarget] 의 범위 설명이다. */
private data class ResetOption(
    val target: com.kosmos.app.domain.memory.ResetTarget,
    @androidx.annotation.StringRes val titleRes: Int,
    @androidx.annotation.StringRes val detailRes: Int
)

private val RESET_OPTIONS = listOf(
    ResetOption(com.kosmos.app.domain.memory.ResetTarget.CONVERSATIONS, R.string.settings_reset_opt_conversations_title, R.string.settings_reset_opt_conversations_detail),
    ResetOption(com.kosmos.app.domain.memory.ResetTarget.TASKS, R.string.settings_reset_opt_tasks_title, R.string.settings_reset_opt_tasks_detail),
    ResetOption(com.kosmos.app.domain.memory.ResetTarget.KNOWLEDGE, R.string.settings_reset_opt_knowledge_title, R.string.settings_reset_opt_knowledge_detail),
    ResetOption(com.kosmos.app.domain.memory.ResetTarget.PROFILE, R.string.settings_reset_opt_profile_title, R.string.settings_reset_opt_profile_detail)
)

/**
 * 데이터 초기화(0.37.1) — 고른 항목만 지운다. 설정·AI 모델·최근 문서 목록은 남는다.
 *
 * [WHY] 확인 대화상자 안에서 고르게 한다 — 섹션에 체크박스를 늘어놓으면 실수로 누른 체크가 화면에 남아 다음 방문 때도 위험하다.
 */
@Composable
private fun DataResetSection(
    state: DataResetState,
    onReset: (Set<com.kosmos.app.domain.memory.ResetTarget>) -> Unit,
    onDismiss: () -> Unit,
    onRestart: () -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    Section(stringResource(R.string.settings_section_data)) {
        com.kosmos.app.ui.component.KosmosListRow(
            title = stringResource(R.string.settings_data_reset_title),
            supporting = stringResource(R.string.settings_data_reset_sub),
            onClick = { showPicker = true },
            trailing = { Chevron() }
        )
    }

    if (showPicker && state !is DataResetState.Done) {
        var selected by remember { mutableStateOf(emptySet<com.kosmos.app.domain.memory.ResetTarget>()) }
        val running = state == DataResetState.Running
        AlertDialog(
            onDismissRequest = { if (!running) showPicker = false },
            title = { Text(stringResource(R.string.settings_reset_dialog_title), color = KosmosTheme.colors.textPrimary) },
            text = {
                Column {
                    RESET_OPTIONS.forEach { option ->
                        val checked = option.target in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(value = checked, enabled = !running, role = androidx.compose.ui.semantics.Role.Checkbox) {
                                    selected = if (checked) selected - option.target else selected + option.target
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                enabled = !running,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = KosmosTheme.colors.dangerText,
                                    uncheckedColor = KosmosTheme.colors.textMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(stringResource(option.titleRes), color = KosmosTheme.colors.textPrimary)
                                Text(stringResource(option.detailRes), style = MaterialTheme.typography.bodySmall, color = KosmosTheme.colors.textMuted)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_reset_dialog_warning) +
                            if (com.kosmos.app.domain.memory.ResetTarget.CONVERSATIONS in selected) stringResource(R.string.settings_reset_dialog_restart_note) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = KosmosTheme.colors.textSecondary
                    )
                    if (state is DataResetState.Failed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = KosmosTheme.colors.dangerText)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onReset(selected) }, enabled = selected.isNotEmpty() && !running) {
                    Text(
                        if (running) stringResource(R.string.settings_reset_deleting) else stringResource(R.string.settings_reset_delete_action),
                        color = if (selected.isNotEmpty() && !running) KosmosTheme.colors.dangerText else KosmosTheme.colors.textMuted
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false; onDismiss() }, enabled = !running) {
                    Text(stringResource(R.string.common_cancel), color = KosmosTheme.colors.textMuted)
                }
            },
            containerColor = KosmosTheme.colors.surface
        )
    }

    if (state is DataResetState.Done) {
        AlertDialog(
            // [WHY] 재시작이 필요한 완료는 닫을 수 없다 — 낡은 메모리 상태로 계속 쓰면 지운 대화가 엔진 KV 에 남아 답에 섞인다.
            onDismissRequest = { if (!state.needsRestart) { showPicker = false; onDismiss() } },
            title = { Text(stringResource(R.string.settings_reset_done_title), color = KosmosTheme.colors.textPrimary) },
            text = {
                Text(
                    if (state.needsRestart) stringResource(R.string.settings_reset_done_restart_body) else stringResource(R.string.settings_reset_done_body),
                    color = KosmosTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (state.needsRestart) onRestart() else { showPicker = false; onDismiss() }
                }) {
                    Text(if (state.needsRestart) stringResource(R.string.settings_reset_restart_action) else stringResource(R.string.common_confirm), color = KosmosTheme.colors.brandText)
                }
            },
            containerColor = KosmosTheme.colors.surface
        )
    }
}
