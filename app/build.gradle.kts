import java.util.Properties

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
fun git(vararg args: String): String? = try {
    val process = ProcessBuilder("git", *args).directory(rootDir).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0) output else null
} catch (e: Exception) {
    null
}

/** Only release tags count: CI's rolling download tags (`dev-pr-12`, `main-latest`) don't. */
val VERSION_TAGS = "v[0-9]*"

fun gitVersionName(): String {
    git("describe", "--tags", "--exact-match", "--match", VERSION_TAGS)?.let { return it }
    val nearestTag = git("describe", "--tags", "--abbrev=0", "--match", VERSION_TAGS) ?: "v0.0"
    val shortHash = git("rev-parse", "--short", "HEAD") ?: "unknown"
    return "$nearestTag+$shortHash"
}

/**
 * Android only installs an update whose versionCode is higher, so it comes from the nearest tag:
 * vX.Y -> X*10000 + Y*100 + 99, and a pre-release vX.Y-alpha.N -> X*10000 + Y*100 + N, so every
 * alpha sorts below its release (v1.0-alpha.8 = 10008, v1.0 = 10099, v1.1 = 10199).
 */
fun gitVersionCode(): Int {
    val tag = git("describe", "--tags", "--abbrev=0", "--match", VERSION_TAGS) ?: return 1
    val m = Regex("""v(\d+)\.(\d+)(?:-alpha\.(\d+))?""").matchEntire(tag) ?: return 1
    val (major, minor, alpha) = m.destructured
    return major.toInt() * 10000 + minor.toInt() * 100 + (alpha.toIntOrNull() ?: 99)
}

android {
    namespace = "com.relay.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.relay.app"
        minSdk = 33
        targetSdk = 35
        versionCode = gitVersionCode()
        resValue("string", "app_name", "Relay")
        versionName = gitVersionName()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Room's exported schemas feed MigrationTestHelper in androidTest.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
        // The in-app guide: docs/usage-guide*.md, copied in at build time (one source of truth).
        getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/guide"))
    }

    // Signing reads keystore.properties at the repo root (git-ignored, never committed):
    //   storeFile=/home/you/relay-release.jks
    //   storePassword=...
    //   keyAlias=relay
    //   keyPassword=...
    // or, in CI, the same four as RELAY_KEYSTORE_FILE, RELAY_KEYSTORE_PASSWORD, RELAY_KEY_ALIAS and
    // RELAY_KEY_PASSWORD (from GitHub secrets, see .github/workflows/build.yml). Without either,
    // release builds come out unsigned and debug builds use the machine's own debug key.
    val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Debug builds install as their own app ("Relay Debug", com.relay.app.debug), so testing
        // never touches the everyday release install or its data.
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Relay Debug")
            // Same key as release when there is one: every CI runner has a different throwaway
            // debug key, and a dev build only installs over the last one if the signature matches.
            if (keystoreProps != null) signingConfig = signingConfigs.getByName("release")
        }
        release {
            // R8: strips unused code (mostly the big icon library) and resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps != null) signingConfig = signingConfigs.getByName("release")
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
        // Findings from before lint ran in CI. Only new ones fail; `./gradlew :app:updateLintBaseline`
        // rewrites it, which should only happen when fixing old findings.
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkDependencies = false
        sarifReport = true
    }

    buildFeatures {
        compose = true
    }
}

val copyUsageGuide = tasks.register<Copy>("copyUsageGuide") {
    from(rootProject.file("docs")) { include("usage-guide*.md") }
    into(layout.buildDirectory.dir("generated/guide/guide"))
}
tasks.named("preBuild") { dependsOn(copyUsageGuide) }

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
