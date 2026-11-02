package com.kosmos.app.feature.chat

import com.kosmos.app.core.common.AppLanguage

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.kosmos.app.R
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.theme.currentAppLanguage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosmos.app.ui.component.kosmosSurface
import com.kosmos.app.feature.splash.splashAnchor

import com.kosmos.app.domain.modelrunner.ModelLoadState
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
    val context = LocalContext.current
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
        IconButton(onClick = onMenuClick) {
            androidx.compose.material3.Icon(
                painter = painterResource(id = com.kosmos.app.R.drawable.ic_ph_list),
                contentDescription = stringResource(R.string.chat_menu_cd),
                tint = KosmosTheme.colors.textSecondary,
                modifier = Modifier.size(com.kosmos.app.ui.component.IconSize.inline)
            )
        }

        Text(
            text = "KOSMOS",
            // [WHY] 하위 화면 상단 바 제목과 같은 `title` 단계 — 메뉴 아이콘도 ← 와 같은 20.
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 1.sp,
            color = KosmosTheme.colors.textPrimary,
            modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                .splashAnchor(com.kosmos.app.feature.splash.SplashAnchor.TITLE)
        )

        // 엔진 상태 별 — Ready=brand / 로딩 계열=muted / 오류·발열 경고=danger·warning. 응답 대기의 반짝이는 별과 같은 모양(0.38.2 M7b).
        val dotColor = when {
            warningMessage != null -> KosmosTheme.colors.warningText
            engineState is ModelLoadState.Ready -> KosmosTheme.colors.brand
            engineState is ModelLoadState.Error || engineState is ModelLoadState.NotFound ->
                KosmosTheme.colors.dangerText
            else -> KosmosTheme.colors.textMuted
        }
        // [WHY] 별 색만으로 엔진 상태를 전했다 — 화면 읽기에도 말한다(DESIGN "색만으로 상태를 전하지 않는다", 0.38.3 점검 P2).
        // 시작 화면 전환에서 우리 별이 이 자리에 앉는다(0.38.4 M6).
        val engineStateDesc = when (engineState) {
            is ModelLoadState.Ready -> stringResource(R.string.chat_engine_state_ready_cd)
            is ModelLoadState.Error -> stringResource(R.string.chat_engine_state_error_cd)
            is ModelLoadState.NotFound -> stringResource(R.string.chat_engine_state_missing_cd)
            else -> stringResource(R.string.chat_engine_state_preparing_cd)
        }
        com.kosmos.app.ui.component.StarGlyph(
            dotColor,
            Modifier.size(12.dp).splashAnchor(com.kosmos.app.feature.splash.SplashAnchor.HEADER_STAR)
                .semantics { contentDescription = engineStateDesc }
        )

        Spacer(modifier = Modifier.weight(1f))

        // [WHY] 별이 노랑·빨강이면 캡슐 글자가 원인을 말한다 — 별 색만으로는 왜인지 알 수 없었다(0.39.3 M3). 수치는 시트에.
        val capsule = capsuleContent(context, engineState, deviceStatus, warningMessage != null)
        if (capsule != null) {
            val capsuleColor = when (capsule.tone) {
                CapsuleTone.DANGER -> KosmosTheme.colors.dangerText
                CapsuleTone.WARNING -> KosmosTheme.colors.warningText
                CapsuleTone.NORMAL -> KosmosTheme.colors.textSecondary
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    // [WHY] 보이는 캡슐은 작게 두고 누르는 영역만 48dp(0.38.3 M6 점검 P1).
                    .minimumInteractiveComponentSize()
                    .kosmosSurface(shape = RoundedCornerShape(99.dp))
                    .clickable(
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClickLabel = stringResource(R.string.chat_view_device_status),
                        onClick = onStatusClick
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                capsule.icon?.let {
                    com.kosmos.app.ui.component.KosmosIcon(
                        it,
                        contentDescription = null,
                        size = 14.dp,
                        tint = capsuleColor,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }
                Text(
                    text = capsule.text,
                    color = capsuleColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
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
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {}
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = KosmosTheme.colors.surface,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
    ) {
        StatusDetailContent(engineState, status, warningMessage, onRetry)
    }
}

/** 상태 시트의 본문 — 엔진이 멈췄으면 원인 문장과 "다시 시도"가 맨 위(0.39.3 M3). 시트 없이 화면 기준 이미지로도 찍는다. */
@Composable
internal fun StatusDetailContent(
    engineState: ModelLoadState,
    status: com.kosmos.app.runtime.metrics.DeviceStatus,
    warningMessage: String?,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val lang = currentAppLanguage()
    Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text(
            text = stringResource(R.string.chat_device_status_title),
            style = MaterialTheme.typography.titleMedium,
            color = KosmosTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))
        engineProblem(context, engineState, lang)?.let { (title, reason) ->
            Text(
                text = title,
                color = KosmosTheme.colors.dangerText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = reason, color = KosmosTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
            com.kosmos.app.ui.component.KosmosButton(stringResource(R.string.common_retry), onRetry, Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = formatDeviceStatus(context, status).ifEmpty { stringResource(R.string.chat_collecting_metrics) },
            color = KosmosTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        val engineLabel = when (engineState) {
            is ModelLoadState.Ready -> stringResource(
                R.string.chat_engine_ready_with_model,
                com.kosmos.app.ui.modelDisplayName(engineState.modelInfo.modelId)
            )
            is ModelLoadState.Error, is ModelLoadState.NotFound -> null
            else -> stringResource(R.string.chat_engine_preparing)
        }
        engineLabel?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = KosmosTheme.colors.textMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (warningMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = warningMessage,
                color = KosmosTheme.colors.warningText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        }
    }
}

/** 엔진이 멈춘 이유 — (제목, 원인 문장). 시작 화면과 같은 문구(ErrorMessages). 멈추지 않았으면 null. */
internal fun engineProblem(
    context: android.content.Context,
    state: ModelLoadState,
    lang: com.kosmos.app.core.common.AppLanguage = com.kosmos.app.core.common.AppLanguage.KO
): Pair<String, String>? = when (state) {
    is ModelLoadState.Error -> context.getString(R.string.splash_engine_error_title) to
        com.kosmos.app.core.mapper.ErrorMessages.userMessage(state.error, lang)
    is ModelLoadState.NotFound -> context.getString(R.string.chat_engine_no_model_title) to
        com.kosmos.app.core.mapper.ErrorMessages.userMessage(com.kosmos.app.core.common.AppError.ModelNotFound(state.expectedPath), lang)
    else -> null
}

internal enum class CapsuleTone { NORMAL, WARNING, DANGER }

/** 헤더 캡슐의 글자·색·아이콘. */
internal data class CapsuleContent(val text: String, val tone: CapsuleTone, @androidx.annotation.DrawableRes val icon: Int?)

/**
 * 캡슐에 무엇을 쓸까 — 엔진이 멈췄으면 "엔진 오류"/"모델 없음", 발열 경고면 "발열 주의 43°", 아니면 온도·메모리 수치. 쓸 것이 없으면 null.
 *
 * [WHY] 원인이 수치보다 먼저다 — 별이 빨개도 캡슐은 회색 수치 그대로라 이유를 알 수 없었다(0.39.3 M3). 엔진 정지가 발열보다 먼저(답을 못 한다).
 */
internal fun capsuleContent(
    context: android.content.Context,
    engineState: ModelLoadState,
    status: com.kosmos.app.runtime.metrics.DeviceStatus,
    heatWarning: Boolean
): CapsuleContent? {
    val stop = com.kosmos.app.R.drawable.ic_ph_warning_circle
    return when {
        engineState is ModelLoadState.Error -> CapsuleContent(
            context.getString(R.string.chat_engine_error_short),
            CapsuleTone.DANGER, stop
        )
        engineState is ModelLoadState.NotFound -> CapsuleContent(
            context.getString(R.string.chat_engine_no_model_short),
            CapsuleTone.DANGER, stop
        )
        heatWarning -> CapsuleContent(
            if (status.temperatureCelsius > 0f) {
                context.getString(R.string.chat_heat_warning_temp, status.temperatureCelsius)
            } else {
                context.getString(R.string.chat_heat_warning_short)
            },
            CapsuleTone.WARNING,
            com.kosmos.app.R.drawable.ic_ph_warning
        )
        else -> formatStatusCapsule(status).takeIf { it.isNotEmpty() }?.let {
            CapsuleContent(it, CapsuleTone.NORMAL, if (status.temperatureCelsius > 0f) com.kosmos.app.R.drawable.ic_ph_thermometer else null)
        }
    }
}

/** 캡슐용 축약 포맷 — `36° · 4.1GB`(온도 아이콘은 화면이 앞에 붙인다). 생성 속도는 기술 수치라 시트에만(0.38.4 M0). */
internal fun formatStatusCapsule(status: com.kosmos.app.runtime.metrics.DeviceStatus): String {
    val parts = mutableListOf<String>()
    if (status.temperatureCelsius > 0f) parts += "%.0f°".format(status.temperatureCelsius)
    // 앱 PSS — 3.6GB 모델이 차지하는 몫. 시스템 전체 사용량은 시트에서 (사용자 요청 2026-08-15).
    if (status.appMemoryBytes > 0L) parts += "%.1fGB".format(status.appMemoryBytes.toGigabytes())
    return parts.joinToString(" · ")
}

/** 한 줄에 하나 — `온도 41.2°C` / `메모리 3.4/8.0GB(앱 4.1GB)` / `생성 속도 12.4 tok/s`. 세 항목 이상을 가운뎃점으로 잇지 않는다(DESIGN). */
internal fun formatDeviceStatus(
    context: android.content.Context,
    status: com.kosmos.app.runtime.metrics.DeviceStatus
): String {
    val parts = mutableListOf<String>()
    if (status.temperatureCelsius > 0f) {
        parts += context.getString(R.string.chat_device_temp, status.temperatureCelsius)
    }
    if (status.memoryTotalBytes > 0L) {
        val used = status.memoryUsedBytes.toGigabytes()
        val total = status.memoryTotalBytes.toGigabytes()
        val ram = context.getString(R.string.chat_device_memory, used, total)
        // [WHY] 앱 PSS 를 함께 보여준다 — 3.7GB 모델이 차지하는 몫이 이 숫자다. 시스템 전체
        // 사용량만 보면 다른 앱과 구분이 안 된다.
        parts += if (status.appMemoryBytes > 0L) {
            val appGb = status.appMemoryBytes.toGigabytes()
            context.getString(R.string.chat_device_memory_with_app, ram, appGb)
        } else {
            ram
        }
    }
    status.tokensPerSecond?.let {
        parts += context.getString(R.string.chat_device_speed, it)
    }
    return parts.joinToString("\n")
}

private fun Long.toGigabytes(): Double = this / 1024.0 / 1024.0 / 1024.0
