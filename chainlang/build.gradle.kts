plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// Pure-Kotlin module — no Java sources — but the jvm plugin still wires up compileJava/
// compileTestJava by default and validates a javac-capable toolchain for them regardless.
// Disable the no-op tasks rather than requiring a full JDK just to build this module.
tasks.named("compileJava") { enabled = false }
tasks.named("compileTestJava") { enabled = false }
