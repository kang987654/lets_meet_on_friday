plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.kosmos.app"
    // [WHY] androidx-hilt 1.4·lifecycle 2.11 이 컴파일 API 37 을 요구한다(AAR 메타데이터).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kosmos.app"
        minSdk = 26
        // [WHY] 35 로 잡아두었으나 "나중에 OS 가 요구할 때 올리는 게 더 귀찮다"는 사용자 결정으로
        // 최신(37)으로 맞췄다(2026-08-14). targetSdk 는 런타임 동작 규칙을 바꾸므로 상향 시
        // 실기기 확인이 짝이다 — 이번 확인 대상: 전경 다운로드 알림, 오디오 녹음, 캘린더 동기화.
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // [WHY] litertlm(20.5MB)·mediapipe(10MB) 네이티브가 4개 ABI 로 들어와 lib/ 이 APK 의
        // 90MB 를 차지했는데, 실행하는 쪽은 그중 하나만 읽는다. 쓰는 둘만 남긴다:
        //   arm64-v8a — 실기기(S25 Ultra, PRD 대상 기기)
        //   x86_64    — 에뮬레이터. **격리된 검증 환경으로 의도적으로 유지한다** — 실기기 DB 에는
        //               실제 대화와 비밀번호가 있어(scratch/lab/device_fixture.py) 자동화 검증을
        //               거기서 돌리면 유출 경로가 생기고 폰에 잔여물도 남는다. 합성 데이터만 넣은
        //               일회용 AVD 에서 기능·회귀를 보고, 충실도(GPU FP16·발열·인셋·체감)만 실기기로.
        //               에뮬레이터는 OpenCL 이 없어 GPU 초기화가 실패하지만 CPU 폴백으로 동작한다.
        // 뺀 둘: x86(구형 에뮬레이터), armeabi-v7a(32비트 기기 — 3.6GB 모델에 12GB+ RAM 이 필요해
        // 애초에 대상 밖).
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // [WHY] 런타임 진단(프리페이스 렌더링, 네이티브 INFO 로그)을 디버그 빌드로 한정하려면
        // BuildConfig.DEBUG 가 필요하다. AGP 8 부터 기본값이 false 라 명시해야 생성된다.
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    lint {
        // [WHY] lint 의 테스트 소스 분석(lintAnalyzeDebugUnitTest)이 **비결정적으로** 죽는다 —
        // BaseAgentStreamTest 해석 중 K2 FIR lazy-resolution 오류가 같은 입력에서 날 때도
        // 안 날 때도 있다(2026-08-14: 크래시 2회, 무변경 재실행 통과 2회. 처음엔 mockk 1.14 를
        // 원인으로 지목했으나 오판 — 버전과 무관하게 재현/비재현이 갈렸다). 복불복 게이트는
        // 게이트가 아니므로 표면을 제거한다. 지금까지 테스트 소스에서 나온 lint 지적은 0건이라
        // 잃는 것이 없다.
        ignoreTestSources = true
        // [WHY] 버전 승격은 lint 알림이 아니라 검증 회차에서 수동 결정한다 — 이 프로젝트에서
        // 런타임 버전은 그 자체가 검증 대상이었다(0.14.0→0.16.0 가설 기각, ADR-016~021).
        // 특히 litertlm·play-services-tflite 계열은 승격 = 실기기 재검증 필수.
        // 대가: 새 버전(보안 패치 포함) 알림이 꺼지므로 확인은 최신화 회차에서 수동으로 한다.
        disable += setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
        )
        // [WHY] 런처 아이콘 모양·중복 지적은 의도적 보류(개인용 앱) — 제대로 고치려면
        // adaptive icon 자산 작업 회차가 필요하다. PNG 파일이라 인라인 억제가 불가능해 여기서 끈다.
        disable += setOf("IconLauncherShape", "IconDuplicates")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // WorkManager (모델 다운로드 전경 작업 — ADR-006)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room ([WHY] 버전 카탈로그로 통일 — 하드코딩 버전과 카탈로그 버전이 갈라지는 드리프트 방지)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)

    ksp(libs.androidx.room.compiler)

    // CameraX

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Coroutines & Collections
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.collections.immutable)

    // LiteRT-LM & TFLite
    implementation(libs.litertlm.android)
    implementation(libs.kotlin.reflect)
    implementation(libs.play.services.tflite.java)
    implementation(libs.play.services.tflite.gpu)
    implementation(libs.play.services.tflite.support)

    // Network
    implementation(libs.okhttp)

    // Compose RichText
    implementation(libs.compose.richtext.commonmark)
    implementation(libs.compose.richtext.ui.material3)

    // Lifecycle
    implementation(libs.androidx.lifecycle.process)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.okhttp.mockwebserver)

    // [WHY] BOM은 configuration별로 적용되므로 androidTest 스코프에도 명시해야 버전이 해석된다.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
