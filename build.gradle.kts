plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt)
}

// Static analysis for both modules in one run (`./gradlew detekt`). Problems that existed when it
// was added are in the baseline, so only new ones fail the build; `./gradlew detektBaseline`
// rewrites it, which should only happen when fixing old findings.
detekt {
    source.setFrom(
        "app/src/main/java", "app/src/test/java", "app/src/androidTest/java",
        "chainlang/src/main/kotlin", "chainlang/src/test/kotlin",
    )
    config.setFrom("config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    baseline = file("config/detekt/baseline.xml")
    parallel = true
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        sarif.required.set(true)
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}
