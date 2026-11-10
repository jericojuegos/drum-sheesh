plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.register("compileDebugKotlin") {
    group = "build"
    description = "Alias for compileKotlin so root ./gradlew compileDebugKotlin includes :core."
    dependsOn("compileKotlin")
}

tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Alias for test so root ./gradlew testDebugUnitTest runs :core JVM tests."
    dependsOn("test")
}
