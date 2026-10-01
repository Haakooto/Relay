plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
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
    namespace = "com.relay.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.relay.app"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = gitVersionName()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Room's exported schemas feed MigrationTestHelper in androidTest.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
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

    lint {
        // values-nb deliberately mirrors values-nn (Nynorsk) for bokmål phones, without listing
        // bokmål as a choice in the system language picker. See NorwegianStringsTest.
        disable += "UnusedTranslation"
    }

    buildFeatures {
        compose = true
    }
}

ksp {
    // Every schema version is exported and committed, so each migration can be tested against
    // the real previous schema (spec §02: real migrations from day one).
    arg("room.schemaLocation", "$projectDir/schemas")
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
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
