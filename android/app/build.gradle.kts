import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val whisperVersion = "1.7.6"
val whisperDir = rootProject.layout.projectDirectory.dir("third_party/whisper.cpp").asFile

val downloadWhisper = tasks.register("downloadWhisper") {
    doLast {
        val marker = whisperDir.resolve("HEX_WHISPER_VERSION")
        val cmake = whisperDir.resolve("CMakeLists.txt")
        if (cmake.isFile && marker.isFile && marker.readText() == whisperVersion) return@doLast
        whisperDir.deleteRecursively()
        val parent = whisperDir.parentFile
        parent.mkdirs()
        val archive = parent.resolve("whisper-$whisperVersion.tar.gz")
        val url = "https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v$whisperVersion.tar.gz"
        val connection = (URI(url).toURL().openConnection() as java.net.HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("User-Agent", "Hex")
        }
        try {
            connection.inputStream.use { input ->
                archive.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        val extract = parent.resolve("whisper-extract")
        extract.deleteRecursively()
        extract.mkdirs()
        val unpack = ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", extract.absolutePath)
            .redirectErrorStream(true)
            .start()
        val unpackLog = unpack.inputStream.bufferedReader().readText()
        if (unpack.waitFor() != 0) error("Couldn't unpack whisper.cpp: $unpackLog")
        val unpacked = extract.listFiles()?.singleOrNull()
            ?: error("whisper.cpp archive did not contain a single top-level directory")
        unpacked.copyRecursively(whisperDir)
        extract.deleteRecursively()
        archive.delete()
        marker.writeText(whisperVersion)
    }
}

tasks.configureEach {
    if (name.contains("CMake") || name.contains("externalNativeBuild")) {
        dependsOn(downloadWhisper)
    }
}

android {
    namespace = "anomaly.hex"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "anomaly.hex"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.savedstate:savedstate:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
