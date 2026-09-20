plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    id("jacoco")
}

android {
    namespace = "com.dailyaipulse"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.dailyaipulse"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            buildConfigField("String", "NEWS_API_KEY", "\"${project.findProperty("NEWS_API_KEY") ?: ""}\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\"")
        }

        debug {
            buildConfigField("String", "NEWS_API_KEY", "\"${project.findProperty("NEWS_API_KEY") ?: ""}\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\"")
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

configurations.all {
    resolutionStrategy {
        // Coil 3's coil-compose pulls in JetBrains Compose Multiplatform's
        // `foundation`/`runtime` artifacts, which declare a Gradle
        // module-metadata constraint requiring kotlin-stdlib 2.4.10. That's
        // newer than this project's Kotlin compiler (2.2.10) can read
        // (metadata format mismatch), breaking compileDebugKotlin. Force the
        // stdlib back to the version matching the Kotlin plugin/compiler in
        // use; the stdlib's own ABI is additive/backward-compatible, so this
        // does not change runtime behavior.
        force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.timber)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Generated-code exclusions shared by both coverage report tasks below (Hilt,
// Moshi adapters, BuildConfig/R/Manifest, Compose preview singletons) so the
// percentage reflects hand-written code, not boilerplate.
val jacocoExcludes = listOf(
    "**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    "android/**/*.*",
    "**/*_Hilt*.*", "**/Hilt_*.*", "**/*_Factory.*", "**/*_MembersInjector.*",
    "**/*_HiltModules*.*", "**/Dagger*.*", "**/*Module.class", "**/*Module\$*.class",
    "**/*JsonAdapter.*",
    "**/ComposableSingletons\$*.*",
    "hilt_aggregated_deps/**/*.*", "dagger/**/*.*", "**/*_GeneratedInjector.*"
)

fun JacocoReport.applyCoverageClassAndSourceDirs() {
    val kotlinClasses = fileTree("${layout.buildDirectory.get()}/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes") {
        exclude(jacocoExcludes)
    }
    val javaClasses = fileTree("${layout.buildDirectory.get()}/intermediates/javac/debug/compileDebugJavaWithJavac/classes") {
        exclude(jacocoExcludes)
    }
    classDirectories.setFrom(files(kotlinClasses, javaClasses))
    sourceDirectories.setFrom(files("$projectDir/src/main/java"))
}

// Coverage report for the debug unit test suite only. No connected
// device/emulator required — safe to run anywhere, including CI.
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "Reporting"
    description = "Generates a JaCoCo coverage report for the debug unit test suite only (no device required)."

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    applyCoverageClassAndSourceDirs()
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) {
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        }
    )

    // This report only covers testDebugUnitTest, so anything only exercised
    // by the instrumented Compose UI suite (articles/ui, sources/ui,
    // navigation, theme, core/di) reads as uncovered here even though it
    // isn't untested — flag that directly in the report itself so the
    // percentage isn't misread as the project's full coverage.
    val indexHtmlFile = layout.buildDirectory.file("reports/jacoco/jacocoTestReport/html/index.html")
    doLast {
        val indexHtml = indexHtmlFile.get().asFile
        if (indexHtml.exists()) {
            val banner = "<div style=\"background:#fff3cd;border:1px solid #ffeeba;padding:12px;" +
                "margin-bottom:16px;font-family:sans-serif;\">⚠️ This report only includes " +
                "unit test coverage (<code>testDebugUnitTest</code>). It does not include this " +
                "project's instrumented Compose UI tests, so <code>articles/ui</code>, " +
                "<code>sources/ui</code>, <code>navigation</code>, <code>theme</code>, and " +
                "<code>core/di</code> show artificially low coverage here. Run " +
                "<code>./gradlew jacocoFullTestReport</code> (requires a connected device/emulator) " +
                "for coverage that includes both.</div>"
            indexHtml.writeText(indexHtml.readText().replace("<h1>app</h1>", "<h1>app</h1>$banner"))
        }
    }
}

// Coverage report combining the unit test suite and the instrumented Compose
// UI test suite, so the percentage reflects all testing written for the app.
// Requires a connected device/emulator, since connectedDebugAndroidTest does.
tasks.register<JacocoReport>("jacocoFullTestReport") {
    dependsOn("testDebugUnitTest", "connectedDebugAndroidTest")
    group = "Reporting"
    description = "Generates a JaCoCo coverage report combining unit and instrumented tests (requires a connected device/emulator)."

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    applyCoverageClassAndSourceDirs()
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) {
            include(
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "outputs/code_coverage/debugAndroidTest/connected/**/coverage.ec"
            )
        }
    )
}