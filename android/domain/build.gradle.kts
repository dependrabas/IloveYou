// The domain module is a *pure Kotlin/JVM* library on purpose.
//
// It holds the business rules of the academy — XP, levels, streaks, quiz
// grading, spaced repetition, recommendations — with no dependency on the
// Android framework. That keeps the rules fast to unit-test on the JVM and
// makes them reusable if the project later grows a web or iOS (KMP) target.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
