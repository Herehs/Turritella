import java.security.MessageDigest

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
}
android {
    buildFeatures { compose = true }
    namespace = "ru.steno"
    compileSdk = 37
    defaultConfig {
        applicationId = "ru.steno"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.environmentVariable("RELEASE_VERSION_CODE").orNull?.toInt() ?: 1
        versionName = providers.environmentVariable("RELEASE_VERSION_NAME").orNull ?: "0.1"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    androidResources { noCompress += listOf("onnx", "task") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "META-INF/DEPENDENCIES" }
    testOptions.unitTests.all { it.useJUnitPlatform() }
    val releaseKey = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
    if (releaseKey != null) {
        signingConfigs.create("release") {
            storeFile = file(releaseKey)
            storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        release {
            // Без ключа локальная release-сборка остаётся неподписанной.
            if (releaseKey != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(files("libs/sherpa.aar"))
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.compose.runtime)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// Тестовые двойники платформенных классов не входят в поставляемый APK.
dependencies {
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

val verifyModels by tasks.registering {
    val directory = layout.projectDirectory.dir("src/main/assets/models")
    val checksums = rootProject.layout.projectDirectory.file("config/models.sha256")
    inputs.dir(directory)
    inputs.file(checksums)
    val stamp = layout.buildDirectory.file("verified/models.sha256")
    outputs.file(stamp)
    doLast {
        for (entry in checksums.asFile.readLines().filter { it.isNotBlank() }) {
            val (expected, name) = entry.split("  ", limit = 2)
            val file = directory.file(name).asFile
            require(file.isFile) { "Отсутствует $name. Запустите scripts/fetch_models.sh" }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(262144)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == expected) { "Повреждён файл $name" }
        }
        val marker = stamp.get().asFile
        marker.parentFile.mkdirs()
        checksums.asFile.copyTo(marker, overwrite = true)
    }
}
tasks.named("preBuild") { dependsOn(verifyModels) }
