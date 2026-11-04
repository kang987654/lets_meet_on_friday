package com.kosmos.app.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kosmos.app.R
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 입력바의 검색 팝업(0.41.2 M4) — 웹 검색·문서 검색 토글 두 개. 하나라도 켜져 있으면 버튼이 강조 색이다.
 *
 * [WHY] 지구본 한 개(웹 검색)를 팝업으로 바꿨다 — 토글이 둘이 되며 입력바 아이콘을 늘리지 않으려고(0.41.2 사용자 결정). 웹 검색의 뜻(위키 툴 게이트)은 그대로다.
 * [WHY] 토글을 눌러도 팝업을 닫지 않는다 — 두 개를 한 번에 바꿀 수 있게.
 * [WHY] 보관함이 비면 문서 검색을 끌 수만 있다 — 켜 둘 문서가 없는데 켜 두면 선언·트리거 비용만 든다(0.41.2 Q2).
 */
@Composable
fun ChatSearchMenu(
    webSearchEnabled: Boolean,
    onToggleWebSearch: (Boolean) -> Unit,
    docSearchEnabled: Boolean,
    onToggleDocSearch: (Boolean) -> Unit,
    docSearchAvailable: Boolean
) {
    var open by remember { mutableStateOf(false) }
    val anyOn = webSearchEnabled || (docSearchEnabled && docSearchAvailable)
    val stateDesc = stringResource(if (anyOn) R.string.chat_state_on else R.string.chat_state_off)
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.size(width = 40.dp, height = 48.dp).semantics { stateDescription = stateDesc }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_ph_magnifying_glass),
                contentDescription = stringResource(R.string.chat_search_menu_cd),
                tint = if (anyOn) KosmosTheme.colors.brandText else KosmosTheme.colors.textMuted,
                modifier = Modifier.size(22.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = KosmosTheme.colors.surface) {
            SearchToggleItem(
                icon = R.drawable.ic_ph_globe,
                title = stringResource(R.string.chat_web_search_cd),
                sub = stringResource(R.string.chat_search_web_sub),
                checked = webSearchEnabled,
                enabled = true,
                onToggle = onToggleWebSearch
            )
            SearchToggleItem(
                icon = R.drawable.ic_ph_file_text,
                title = stringResource(R.string.chat_search_docs),
                sub = stringResource(if (docSearchAvailable) R.string.chat_search_docs_sub else R.string.chat_search_docs_empty),
                checked = docSearchEnabled && docSearchAvailable,
                enabled = docSearchAvailable,
                onToggle = onToggleDocSearch
            )
        }
    }
}

@Composable
private fun SearchToggleItem(icon: Int, title: String, sub: String, checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = KosmosTheme.colors
    val stateDesc = stringResource(if (checked) R.string.chat_state_on else R.string.chat_state_off)
    DropdownMenuItem(
        // [WHY] 행 하나가 스위치 역할 — 행과 스위치가 따로 포커스를 잡으면 화면 읽기가 두 번 읽는다(SettingSwitchRow 와 같은 결).
        modifier = Modifier.semantics {
            role = Role.Switch
            stateDescription = stateDesc
        },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, tint = if (enabled) colors.textSecondary else colors.textMuted, modifier = Modifier.size(20.dp)) },
        text = {
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) colors.textPrimary else colors.textMuted)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        },
        trailingIcon = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.padding(start = 8.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.surface,
                    checkedTrackColor = colors.brand,
                    checkedBorderColor = colors.brand,
                    uncheckedThumbColor = colors.textMuted,
                    uncheckedTrackColor = colors.surface,
                    uncheckedBorderColor = colors.textMuted
                )
            )
        },
        enabled = enabled,
        onClick = { onToggle(!checked) }
    )
}
