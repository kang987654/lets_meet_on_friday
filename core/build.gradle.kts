plugins {
    kotlin("jvm")
}

dependencies {
    // [WHY] CurrentLanguage.languageFlow 가 StateFlow 를 공개 시그니처에 노출하므로 api 로 연다 (0.40.3 M1).
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
}
