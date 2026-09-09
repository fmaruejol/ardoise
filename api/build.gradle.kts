import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :api is pure Kotlin/JVM as well. It brings the Ktor *client* but no engine, and
// :app picks the engine, which keeps this module runnable in plain JVM tests.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    api(project(":core"))

    api(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    // A real engine, only for tests that talk to MockWebServer.
    testImplementation(libs.ktor.client.okhttp)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
