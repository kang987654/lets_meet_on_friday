plugins {
    id("com.android.library")
    // [WHY] 이 플러그인이 없으면 :data 안에서 선언한 @Serializable 클래스의 직렬화기가
    // 생성되지 않아, 컴파일은 통과하지만 런타임에 SerializationException 이 난다.
    // ExportManifest(내보내기 manifest.json)와 PartMeta(다운로드 이어받기)가 여기 해당한다.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.kosmos.app.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core"))

    // [WHY] Uri 조립을 KTX 확장(toUri)으로 쓰므로 전이 의존에 기대지 않고 명시한다
    // (app 모듈이 core-ktx 를 명시한 것과 같은 이유).
    implementation(libs.androidx.core.ktx)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Network
    implementation(libs.okhttp)
    
    // MediaPipe
    implementation(libs.mediapipe.tasks.text)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
}
