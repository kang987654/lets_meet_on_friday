plugins {
    kotlin("jvm")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.javax.inject)
    implementation(libs.androidx.paging.common)
    implementation(project(":core"))
}
