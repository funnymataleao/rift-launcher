// Top-level build file where you can add configuration options common to all sub-projects/modules.
// D8 8.8.18, the one AGP 8.8 ships, crashes with a NullPointerException dexing the debug build of
// RiftHomeScreen (a very large composable); D8 8.9 handles it. Only debug builds take the newer
// version, so release builds keep the R8 that AGP ships with.
buildscript {
    if (gradle.startParameter.taskNames.any { it.contains("Debug") }) {
        repositories { google() }
        dependencies { classpath("com.android.tools:r8:8.9.27") }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.dagger.hilt) apply false
    alias(libs.plugins.jetbrains.jvm) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.jetbrains.serialization) apply false
    alias(libs.plugins.kotlinter) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.secrets.gradle) apply false
}

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
    resolutionStrategy.cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
}
