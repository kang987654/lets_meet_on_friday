package com.kosmos.app.feature.settings

import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosmos.app.R
import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.common.ResponseStyle
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.platform.notification.NotificationAccess
import com.kosmos.app.platform.speech.SpeechOutput
import com.kosmos.app.ui.component.KosmosListRow
import com.kosmos.app.ui.component.KosmosSegmentedControl
import com.kosmos.app.ui.component.KosmosTopBar
import com.kosmos.app.ui.restartApp
import com.kosmos.app.ui.theme.ChatTextSize
import com.kosmos.app.ui.theme.FontMode
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.theme.SplashStyle
import com.kosmos.app.ui.theme.ThemeMode
import com.kosmos.app.ui.theme.ThemeViewModel
import com.kosmos.app.ui.theme.currentAppLanguage
import com.kosmos.app.ui.theme.scaled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
    onNavigateToModelManagement: () -> Unit = {},
    onNavigateToMemoryCleanup: () -> Unit = {},
    dataResetViewModel: DataResetViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    // [WHY] 뜻 검색 모델(선택 설치, 0.41.2)은 자기 ViewModel 을 가진 슬롯 — 모델 관리 화면은 채팅 모델이 없을 때만 열려 설치된 사용자가 닿지 못한다.
    // 기본값은 빈 칸이라 화면 카탈로그·테스트는 그대로다.
    embeddingSection: @Composable () -> Unit = {}
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
        KosmosTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        // [WHY] 섹션마다 큰 상자 + 톱니 아이콘 + 자간 넓힌 제목(eyebrow)이었다 — 섹션 제목 + 목록 묶음 + 각주로(0.38.3 M3 시안 A, impeccable "설정은 묶음 목록").
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Section(stringResource(R.string.settings_section_model)) { ModelStatus(uiState.modelLoadState, onNavigateToModelManagement, viewModel::refreshModelState) }
            embeddingSection()

            // [WHY] 고르는 값은 세그먼트로 바로 바꾼다(시안 A, 사용자 선택) — 값 행 + 시트는 한 번 더 눌러야 하고 글자 크기 미리보기가 시트로 숨는다.
            Section(stringResource(R.string.settings_section_display), footnote = stringResource(R.string.settings_display_footnote)) {
                Column(Modifier.padding(16.dp)) {
                    // [WHY] LocaleManager 는 Android 13(API 33)+ 전용이라 리소스 기반 전환이 동작하지 않는 API 32 이하에서는 숨긴다(0.40.1 결정).
                    val showLanguageToggle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    if (showLanguageToggle) {
                        ControlLabel(stringResource(R.string.settings_control_language))
                        KosmosSegmentedControl(
                            options = listOf(
                                AppLanguage.Option.SYSTEM to stringResource(R.string.lang_option_system),
                                AppLanguage.Option.KO to stringResource(R.string.lang_option_ko),
                                AppLanguage.Option.EN to stringResource(R.string.lang_option_en)
                            ),
                            selected = languageOption,
                            onSelect = themeViewModel::setLanguageOption
                        )
                    }
                    ControlLabel(stringResource(R.string.settings_control_theme), top = if (showLanguageToggle) 16.dp else 0.dp)
                    KosmosSegmentedControl(
                        options = ThemeMode.entries.map { mode ->
                            val label = when (mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
                            }
                            mode to label
                        },
                        selected = themeMode,
                        onSelect = themeViewModel::setThemeMode
                    )
                    // [WHY] 하다 라운드가 기본이지만 기기 글꼴을 쓰는 사람도 있다 — 크기·굵기 단계는 같고 글꼴만 바뀐다(DESIGN.md v3 Typography).
                    ControlLabel(stringResource(R.string.settings_control_font), top = 16.dp)
                    KosmosSegmentedControl(
                        options = FontMode.entries.map { mode ->
                            val label = when (mode) {
                                FontMode.APP -> stringResource(R.string.font_mode_app)
                                FontMode.SYSTEM -> stringResource(R.string.font_mode_system)
                            }
                            mode to label
                        },
                        selected = fontMode,
                        onSelect = themeViewModel::setFontMode
                    )
                    // [WHY] 채팅 메시지만 키운다(0.38.2 사용자 결정) — 화면 틀은 시스템 글자 크기를 따른다.
                    ControlLabel(stringResource(R.string.settings_control_chat_text_size), top = 16.dp)
                    KosmosSegmentedControl(
                        options = ChatTextSize.entries.map { size ->
                            val label = when (size) {
                                ChatTextSize.SMALL -> stringResource(R.string.chat_text_size_small)
                                ChatTextSize.DEFAULT -> stringResource(R.string.chat_text_size_default)
                                ChatTextSize.LARGE -> stringResource(R.string.chat_text_size_large)
                                ChatTextSize.XLARGE -> stringResource(R.string.chat_text_size_xlarge)
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
                    KosmosSegmentedControl(
                        options = SplashStyle.entries.map { style ->
                            val label = when (style) {
                                SplashStyle.PLANET -> stringResource(R.string.splash_style_planet)
                                SplashStyle.GALAXY -> stringResource(R.string.splash_style_galaxy)
                            }
                            style to label
                        },
                        selected = splashStyle ?: SplashStyle.PLANET,
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
                    KosmosSegmentedControl(
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
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { viewModel.onMaxTokensChanged(sliderValue.toInt()) },
                        valueRange = Constants.MIN_CONTEXT_TOKENS.toFloat()..Constants.MAX_CONTEXT_TOKENS.toFloat(),
                        steps = (Constants.MAX_CONTEXT_TOKENS - Constants.MIN_CONTEXT_TOKENS) / 100 - 1, // 100 단위
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
            val briefingWarning = if (uiState.briefingEnabled && (notificationDenied || !NotificationAccess.canPost(briefingContext))) stringResource(R.string.notif_permission_denied_message) else null
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
                    KosmosListRow(
                        title = stringResource(R.string.settings_briefing_time_row),
                        onClick = { showTimePicker = true },
                        trailing = { ValueTrailing(IsoDateTimeParser.clockKorean(uiState.briefingTimeMinutes, lang)) }
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
                KosmosListRow(
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
                SpeechOutput.VoiceStatus.NO_KOREAN_OFFLINE_VOICE ->
                    stringResource(R.string.settings_voice_no_offline_warning)
                SpeechOutput.VoiceStatus.INIT_FAILED ->
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
                val engines = listOf(SpeechOutput.Engine("", stringResource(R.string.settings_voice_engine_default))) + ttsEngines
                engines.forEachIndexed { index, engine ->
                    KosmosListRow(
                        title = engine.label,
                        modifier = Modifier.selectable(
                            selected = uiState.ttsEngine == engine.packageName,
                            role = Role.RadioButton,
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
            if (uiState.voiceStatus == SpeechOutput.VoiceStatus.NO_KOREAN_OFFLINE_VOICE) {
                TextButton(onClick = {
                    val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                        .apply { if (uiState.ttsEngine.isNotBlank()) setPackage(uiState.ttsEngine) }
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // [WHY] 엔진이 설치 화면을 제공하지 않을 수 있다 — 실패는 조용히 무시(안내 문구는 이미 보이고 있다).
                    runCatching { ttsContext.startActivity(intent) }
                }) { Text(stringResource(R.string.settings_voice_install_data), color = KosmosTheme.colors.brandText) }
            }

            // 6. 리마인더 정확 알람 안내 (B1) — 권한이 없을 때만 노출
            val context = LocalContext.current
            var exactAlarmAllowed by remember { mutableStateOf(true) }
            val lifecycleOwner = LocalLifecycleOwner.current
            // [WHY] canScheduleExactAlarms 에는 Flow 가 없다 — 시스템 설정에서 돌아오는
            // 순간(ON_RESUME)에 재확인해야 허용 직후 이 안내가 사라진다. SettingsViewModel 의
            // combine 은 5개 상한에 닿아 있으므로(0.20.0 경계 기록) 시스템 API 상태는 화면에서
            // 직접 읽는다 — DataStore 상태가 아니라 combine 에 넣을 이유도 없다.
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        exactAlarmAllowed = NotificationAccess.canScheduleExact(context)
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
                    KosmosListRow(
                        title = stringResource(R.string.settings_exact_alarm_allow),
                        onClick = {
                            context.startActivity(NotificationAccess.exactAlarmSettingsIntent(context))
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
                onRestart = { restartApp(context) }
            )

            // [WHY] SECURITY & LOGS 섹션은 제거했다 — 활동 기록 진입점이 드로어 타일로 옮겨져
            // (M2-2) 같은 화면으로 가는 문이 두 개였다 (2026-08-15 사용자 피드백).
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
