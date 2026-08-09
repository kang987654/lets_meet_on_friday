package com.kosmos.app.platform.device

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 한 시점의 메모리 사용량 스냅샷입니다. 값은 모두 바이트입니다.
 *
 * @property appBytes 이 앱 프로세스의 PSS. 3.7GB 모델은 네이티브/mmap 이라 **자바 힙 수치로는
 *   전혀 보이지 않으므로** PSS 여야 의미가 있다.
 */
data class MemorySnapshot(
    val usedBytes: Long,
    val totalBytes: Long,
    val appBytes: Long,
    val lowMemory: Boolean
)

interface DeviceResourceProvider {
    suspend fun memorySnapshot(): MemorySnapshot
}

/**
 * [AndroidDeviceResourceProvider]
 * 시스템 전체 메모리와 이 앱의 PSS 를 읽습니다.
 *
 * ### Architecture Context
 * - **Layer**: Platform (Device Capability)
 *
 * [WHY] **GPU 사용률은 제공하지 않는다.** 안드로이드에 공개 API 가 없고, 벤더 sysfs
 * (Adreno `/sys/class/kgsl/kgsl-3d0/gpubusy`)는 SELinux 로 앱 도메인에서 차단된다. 그 자리는
 * 토큰 생성 속도(tok/s)가 대신한다 — 발열로 느려지면 즉시 떨어지는 실측값이다 (ADR-015).
 *
 * [WHY] `Debug.getMemoryInfo` 는 프로세스 맵을 훑기 때문에 수십 ms 가 걸린다. 반드시 워커
 * 스레드에서 부르고, 호출 간격도 초 단위로 유지해야 한다 — 그래서 `suspend` 계약이다.
 */
class AndroidDeviceResourceProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceResourceProvider {

    private val activityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    override suspend fun memorySnapshot(): MemorySnapshot =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val info = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(info)

            val appPssBytes = runCatching {
                val debugInfo = Debug.MemoryInfo()
                Debug.getMemoryInfo(debugInfo)
                debugInfo.totalPss.toLong() * 1024L // totalPss 는 KB 단위다
            }.getOrDefault(0L)

            MemorySnapshot(
                usedBytes = (info.totalMem - info.availMem).coerceAtLeast(0L),
                totalBytes = info.totalMem,
                appBytes = appPssBytes,
                lowMemory = info.lowMemory
            )
        }
}
