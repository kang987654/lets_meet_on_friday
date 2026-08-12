package com.kosmos.app.contract

import com.kosmos.app.domain.model.AuditEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [AuditEventTypeSpecTest]
 * `AuditEventType` enum 과 `docs/api_spec.yaml` 의 `AuditEventType` 스키마가 서로 어긋나지
 * 않는지 검증합니다.
 *
 * [WHY] 앞선 `ContractConsistencyTest` 는 이름이 "matches api spec" 이었지만 스펙 파일을 읽지
 * 않았다. 10개 값 중 6개가 **존재하는지**만 봐서, 한쪽에만 값이 추가되거나 빠져도 통과했다.
 * 이름이 약속한 것을 실제로 하도록 옮겨 쓴다 — 양방향 집합 비교라 어느 쪽이 앞서 나가도 잡힌다.
 */
class AuditEventTypeSpecTest {

    @Test
    fun `AuditEventType 은 api_spec 선언과 정확히 일치한다`() {
        val declaredInSpec = parseEnumValues(specFile().readText(), "AuditEventType")
        val declaredInCode = AuditEventType.entries.map { it.name }.toSet()

        assertTrue("api_spec.yaml 에서 AuditEventType enum 목록을 찾지 못했다", declaredInSpec.isNotEmpty())
        // [WHY] 집합 비교라 스펙에만 있는 값과 코드에만 있는 값이 함께 드러난다. 한쪽만 고치고
        // 다른 쪽을 잊는 것이 이 계약이 실제로 깨지는 방식이다.
        assertEquals(
            "AuditEventType 이 docs/api_spec.yaml 과 어긋났다 — 양쪽을 함께 고칠 것",
            declaredInSpec,
            declaredInCode
        )
    }

    /**
     * `<name>:` 스키마 블록 안의 `enum:` 목록에서 `- VALUE` 항목을 모읍니다.
     *
     * [WHY] YAML 파서를 의존성으로 들이지 않는다. 이 스키마는 `- 대문자_이름` 한 줄짜리 항목만
     * 쓰므로 그 형태를 벗어나면 목록이 비고, 위의 isNotEmpty 단언이 조용한 통과를 막는다.
     */
    private fun parseEnumValues(yaml: String, schemaName: String): Set<String> {
        val lines = yaml.lines()
        val schemaAt = lines.indexOfFirst { it.trimEnd() == "    $schemaName:" }
        if (schemaAt < 0) return emptySet()

        val enumAt = (schemaAt until lines.size).firstOrNull { lines[it].trim() == "enum:" } ?: return emptySet()
        val itemPattern = Regex("""^\s+- ([A-Z][A-Z0-9_]*)\s*$""")
        return lines.asSequence()
            .drop(enumAt + 1)
            .map { itemPattern.find(it)?.groupValues?.get(1) }
            .takeWhile { it != null }
            .filterNotNull()
            .toSet()
    }

    /**
     * 저장소 루트를 거슬러 올라가며 `docs/api_spec.yaml` 을 찾습니다.
     *
     * [WHY] 테스트 작업 디렉터리는 실행 주체(Gradle, IDE)에 따라 모듈 폴더일 수도 루트일 수도
     * 있다. 상대 경로를 고정하면 한쪽에서만 통과한다.
     */
    private fun specFile(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "docs/api_spec.yaml")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        throw AssertionError("docs/api_spec.yaml 을 찾지 못했다 (cwd=${File(".").absolutePath})")
    }
}
