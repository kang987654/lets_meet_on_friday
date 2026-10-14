package com.kosmos.app.ui

/**
 * 앱 프로세스를 새로 시작합니다 — 백업 복원·대화 기록 초기화의 마지막 단계.
 *
 * [WHY] 두 경우 모두 현재 프로세스의 상태가 낡는다 — 복원은 열려 있는 Room DB 파일을 교체하고(`ExportImportManager`),
 * 대화 초기화는 엔진의 대화 KV·화면 상태가 지운 대화를 들고 있다. 사용자가 "재시작"을 누른 시점에만 부른다.
 */
internal fun restartApp(context: android.content.Context) {
    val componentName = context.packageManager
        .getLaunchIntentForPackage(context.packageName)?.component
    if (componentName != null) {
        context.startActivity(android.content.Intent.makeRestartActivityTask(componentName))
    }
    kotlin.system.exitProcess(0)
}
