plugins {
    kotlin("jvm")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.javax.inject)
    // [WHY] AppResult/AppError 등 :core 타입이 domain의 공개 시그니처에 노출되므로 api로 전이해야
    // 소비 모듈이 :core를 중복 선언하지 않아도 된다.
    api(project(":core"))
}
