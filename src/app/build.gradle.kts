import java.security.MessageDigest

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlin)
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
}
android {
    buildFeatures { compose = true }
    namespace = "ru.steno"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.steno"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    androidResources { noCompress += listOf("onnx", "task") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "META-INF/DEPENDENCIES" }
    testOptions.unitTests.isIncludeAndroidResources = true
    testOptions.unitTests.all { it.useJUnitPlatform() }
}
dependencies {
    implementation("androidx.navigation3:navigation3-runtime:1.0.0-alpha02")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation(files("libs/sherpa.aar"))
    implementation("com.google.mediapipe:tasks-genai:${libs.versions.llm.get()}")
    implementation("androidx.room:room-runtime:${libs.versions.room.get()}")
    implementation("androidx.room:room-ktx:${libs.versions.room.get()}")
    ksp("androidx.room:room-compiler:${libs.versions.room.get()}")
    implementation("androidx.work:work-runtime-ktx:${libs.versions.work.get()}")
    implementation("io.insert-koin:koin-android:${libs.versions.koin.get()}")
    implementation("io.insert-koin:koin-androidx-workmanager:${libs.versions.koin.get()}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.media3:media3-exoplayer:${libs.versions.media.get()}")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("androidx.compose.runtime:runtime:1.7.8")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.7.8")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.8")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:6.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

// Тестовые двойники платформенных классов не входят в поставляемый APK.
dependencies {
    testImplementation("io.mockk:mockk:1.13.17")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
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
