plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.kosmos.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kosmos.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
