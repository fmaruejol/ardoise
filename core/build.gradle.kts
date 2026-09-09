import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core is pure Kotlin/JVM on purpose. It must never depend on the Android SDK,
// which is what keeps the money and settlement logic testable in milliseconds.
plugins {
    alias(libs.plugins.kotlin.jvm)
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
    // One declaration needs this: GroupPreferences, the port for what must
    // survive an uninstall, returns Flow. `api` rather than `implementation`
    // because that Flow is in its public signature, so :app sees the type.
    //
    // There are no repository interfaces here, because the repositories live in :app.
    // Should GroupPreferences ever move there too, this dependency goes with
    // it and :core becomes pure Kotlin with no third-party code at all.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
