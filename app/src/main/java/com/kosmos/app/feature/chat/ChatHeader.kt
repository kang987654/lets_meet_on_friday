package com.kosmos.app.feature.chat

import com.kosmos.app.ui.theme.KosmosTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.kosmos.app.domain.model.ChatMessage
import androidx.compose.ui.unit.sp
import com.kosmos.app.ui.component.glassEffect
import kotlinx.coroutines.launch

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
// [WHY] ChatScreen.kt(1,200여 줄, 12개 컴포저블 동거)를 응집 단위로 분리했다
// (2026-08-15, MVP 감사 refactor-ui) — 순수 이동이며 동작 변경이 없다. 이 파일: 상단 헤더와 기기 상태 표시.

/**
 * 슬림 헤더 (시안 A′, M2-1): ☰ + "KOSMOS" + 엔진 상태 점 + 상태 캡슐 1개.
 *
 * [WHY] 웹 검색 토글은 입력바로, 설정은 드로어(M2-2)로 갔다 — 우측 아이콘 밀집(토글·설정
 * 겹침, ui_improvement_plan 보류 항목)이 원천 소멸한다. 상시 상태 스트립은 캡슐로 압축되어
 * 세로 한 줄을 회수하고, 상세 수치는 캡슐 탭 → [StatusDetailSheet] 로 옮겼다.
 */
@Composable
fun CustomChatHeader(
    onMenuClick: () -> Unit = {},
    engineState: ModelLoadState = ModelLoadState.Loading,
    deviceStatus: com.kosmos.app.runtime.metrics.DeviceStatus =
        com.kosmos.app.runtime.metrics.DeviceStatus(),
    warningMessage: String? = null,
    onStatusClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // [WHY] 이 헤더는 TopAppBar 가 아니라 평범한 Row 다 — M3 Scaffold 는 topBar 슬롯의
            // 상태바 인셋을 topBar 자신이 처리한다고 가정한다(TopAppBar 는 내장). M2-2 에서
            // 셸 Scaffold 를 제거하자 이 가정이 드러나 헤더가 상태바 밑에 깔렸다(2026-08-15
            // 실기기: 헤더·입력바가 잘려 조작 불능). 인셋은 여기서 직접 진다.
            .statusBarsPadding()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(40.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = KosmosTheme.colors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Text(
            text = "KOSMOS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 1.sp,
            color = KosmosTheme.colors.textPrimary,
            modifier = Modifier.padding(start = 4.dp, end = 8.dp)
        )

        // 엔진 상태 점 — Ready=success / 로딩 계열=muted / 오류·발열 경고=danger·warning.
        val dotColor = when {
            warningMessage != null -> KosmosTheme.colors.warning
            engineState is ModelLoadState.Ready -> KosmosTheme.colors.success
            engineState is ModelLoadState.Error || engineState is ModelLoadState.NotFound ->
                KosmosTheme.colors.danger
            else -> KosmosTheme.colors.textMuted
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(dotColor)
        )

        Spacer(modifier = Modifier.weight(1f))

        // [WHY] 발열 경고 시 캡슐이 warning 색으로 승격된다 — 상시 스트립을 없애도 발열
        // 가시성은 유지해야 한다 (기존 스트립의 경고 합류 설계를 캡슐이 승계).
        val capsule = formatStatusCapsule(deviceStatus)
        if (capsule.isNotEmpty() || warningMessage != null) {
            val warning = warningMessage != null
            Text(
                text = if (warning) "⚠ ${capsule.ifEmpty { "발열 주의" }}" else capsule,
                color = if (warning) KosmosTheme.colors.warning else KosmosTheme.colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .glassEffect(shape = RoundedCornerShape(99.dp))
                    .clickable(onClick = onStatusClick)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

/**
 * 기기 상태 상세 시트 — 캡슐 탭으로 열립니다. 기존 상시 스트립의 본문이 이곳으로 이동했다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusDetailSheet(
    engineState: ModelLoadState,
    status: com.kosmos.app.runtime.metrics.DeviceStatus,
    warningMessage: String?,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = KosmosTheme.colors.surface,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                text = "기기 상태",
                style = MaterialTheme.typography.titleMedium,
                color = KosmosTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = formatDeviceStatus(status).ifEmpty { "수치를 수집하는 중이에요." },
                color = KosmosTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            val engineLabel = when (engineState) {
                is ModelLoadState.Ready -> "엔진 준비됨 (${engineState.modelInfo.modelId})"
                is ModelLoadState.Error -> "엔진 오류"
                is ModelLoadState.NotFound -> "모델 파일 없음"
                else -> "엔진 준비 중"
            }
            Text(
                text = engineLabel,
                color = KosmosTheme.colors.textMuted,
                style = MaterialTheme.typography.bodySmall
            )
            if (warningMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = warningMessage,
                    color = KosmosTheme.colors.warning,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            }
        }
    }
}

/** 캡슐용 축약 포맷 — `🌡36° · 9.6t/s`. 상세 수치는 [formatDeviceStatus](시트)가 담당한다. */
internal fun formatStatusCapsule(status: com.kosmos.app.runtime.metrics.DeviceStatus): String {
    val parts = mutableListOf<String>()
    if (status.temperatureCelsius > 0f) parts += "🌡%.0f°".format(status.temperatureCelsius)
    status.tokensPerSecond?.let { parts += "%.1ft/s".format(it) }
    return parts.joinToString(" · ")
}
/**
 * 채팅 상단에 상시 표시되는 기기 상태 줄입니다.
 *
 * [WHY] 발열 경고 배너를 따로 두지 않고 여기에 합쳤다. 평소에는 수치만 옅게 보이고, 경고·임계
 * 온도에서는 같은 줄이 `warning` 색으로 승격되며 안내 문구가 붙는다. 배너와 상태 줄이 따로
 * 있으면 발열 상황에서 화면 위쪽이 두 겹으로 밀린다.
 *
 * [WHY] GPU 사용률은 넣지 않는다 — 안드로이드에 공개 API 가 없고 벤더 sysfs 는 SELinux 로
 * 막혀 있다. 그 자리를 토큰 생성 속도가 대신한다 (ADR-015).
 */
@Composable
fun DeviceStatusStrip(
    status: com.kosmos.app.runtime.metrics.DeviceStatus,
    warningMessage: String?
) {
    // 온도가 아직 0 이면(첫 갱신 전) 자리만 차지하지 않도록 접어 둔다.
    if (status.temperatureCelsius <= 0f && warningMessage == null) return

    val isWarning = warningMessage != null
    val tint = if (isWarning) KosmosTheme.colors.warning else KosmosTheme.colors.textMuted
    val background =
        if (isWarning) KosmosTheme.colors.warning.copy(alpha = 0.15f)
        else androidx.compose.ui.graphics.Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = formatDeviceStatus(status),
            color = tint,
            style = MaterialTheme.typography.labelSmall
        )
        if (warningMessage != null) {
            Text(
                text = warningMessage,
                color = KosmosTheme.colors.warning,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** `🌡 41.2°C · RAM 3.4/8.0GB(앱 4.1GB) · 12.4 tok/s` */
internal fun formatDeviceStatus(status: com.kosmos.app.runtime.metrics.DeviceStatus): String {
    val parts = mutableListOf<String>()
    if (status.temperatureCelsius > 0f) {
        parts += "🌡 %.1f°C".format(status.temperatureCelsius)
    }
    if (status.memoryTotalBytes > 0L) {
        val ram = "RAM %.1f/%.1fGB".format(
            status.memoryUsedBytes.toGigabytes(),
            status.memoryTotalBytes.toGigabytes()
        )
        // [WHY] 앱 PSS 를 함께 보여준다 — 3.7GB 모델이 차지하는 몫이 이 숫자다. 시스템 전체
        // 사용량만 보면 다른 앱과 구분이 안 된다.
        parts += if (status.appMemoryBytes > 0L) {
            "$ram(앱 %.1fGB)".format(status.appMemoryBytes.toGigabytes())
        } else {
            ram
        }
    }
    status.tokensPerSecond?.let { parts += "%.1f tok/s".format(it) }
    return parts.joinToString("  ·  ")
}

private fun Long.toGigabytes(): Double = this / 1024.0 / 1024.0 / 1024.0
