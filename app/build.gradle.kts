plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * The nearest annotated git tag is the real version — manual, deliberate, one per release.
 * Every commit between tags shows as `<tag>+<short-hash>` (e.g. "v0.1+797fbfc") rather than
 * silently reusing the last tag's number, so a build installed from an untagged commit is never
 * mistaken for the tagged release it's actually a few commits ahead of. Falls back to "v0.0" if
 * this checkout has no tags at all (a fresh clone before the first release).
 */
fun gitVersionName(): String {
    fun run(vararg command: String): String? = try {
        val process = ProcessBuilder(*command).directory(rootDir).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0) output else null
    } catch (e: Exception) {
        null
    }

    run("git", "describe", "--tags", "--exact-match")?.let { return it }
    val nearestTag = run("git", "describe", "--tags", "--abbrev=0") ?: "v0.0"
    val shortHash = run("git", "rev-parse", "--short", "HEAD") ?: "unknown"
    return "$nearestTag+$shortHash"
}

android {
    namespace = "com.clockr.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.clockr.app"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = gitVersionName()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(project(":chainlang"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.androidx.ui.tooling)
}
