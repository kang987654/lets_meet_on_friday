package com.kosmos.app.runtime.gemma

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [OpenClManifestTest]
 * 매니페스트의 OpenCL 사용 선언(0.41.5) — 빠지면 컴파일·테스트는 그대로 통과하고 휴대폰에서만 GPU 가 OpenCL 대신 WebGPU 로 내려간다.
 * play-services-tflite 를 빼면서 그것이 합쳐 넣던 선언이 사라진 일을 휴대폰 로그로 잡았다 — 그 회귀를 JVM 에서 막는다.
 */
class OpenClManifestTest {

    @Test
    fun `OpenCL 라이브러리 세 이름을 선택 사항으로 선언한다`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        for (lib in listOf("libOpenCL.so", "libOpenCL-car.so", "libOpenCL-pixel.so")) {
            val declared = Regex("""<uses-native-library\s+android:name="${Regex.escape(lib)}"\s+android:required="false"\s*/>""")
            assertTrue("$lib 선언이 없다", declared.containsMatchIn(manifest))
        }
    }
}
