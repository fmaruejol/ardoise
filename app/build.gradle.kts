plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    // Checked in, so a schema change shows up in review rather than only at
    // runtime on somebody's phone.
    schemaDirectory("$projectDir/schemas")
}

// The release key lives outside the repo and reaches the build only through
// the environment, so a clone can build everything but a signed release, and
// nothing here can leak it. Absent, `release` stays unsigned rather than
// failing: that is the ordinary case for anyone building from source.
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

// The tag CI was run on decides the version. Defaults keep a local build
// working; a release never uses them.
val appVersionCode = (providers.gradleProperty("appVersionCode").orNull ?: "1").toInt()
val appVersionName = providers.gradleProperty("appVersionName").orNull ?: "0.0.0-dev"

android {
    namespace = "io.github.fmaruejol.ardoise"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.fmaruejol.ardoise"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            // Null when no key is in the environment, which leaves the build
            // unsigned instead of failing.
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric needs the merged resources to inflate a theme.
            // Note this is NOT returnDefaultValues, which would stub the
            // framework out and make tests pass while testing nothing.
            isIncludeAndroidResources = true
            all {
                // Robolectric boots a framework per test class; the default
                // test heap runs out once there is more than a screen or two
                // of them, and it surfaces as every Compose test failing at
                // once rather than as anything to do with the change made.
                it.maxHeapSize = "2g"
            }
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":api"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Group ids and the active participant are the only credentials Spliit has,
    // so they are persisted. Everything else comes from the network.
    implementation(libs.androidx.datastore.preferences)

    // Adding a group by QR code. The decoder reads a CameraX frame directly,
    // so the camera screen is ours rather than a library activity.
    implementation(libs.zxing.cpp)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view) {
        // Only PreviewView is used from camera-view, and use cases are bound
        // by hand rather than through CameraController, which is the one class
        // here that needs camera-video. Excluding it drops media3, which came
        // with the muxer and brought ACCESS_NETWORK_STATE with it.
        exclude(group = "androidx.camera", module = "camera-video")
    }

    // The offline read cache. Room's DAO Flows are what re-emit a screen when
    // its data changes; nothing else in the app announces staleness.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // The Ktor engine lives here, not in :api.
    implementation(libs.ktor.client.okhttp)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.rules)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
