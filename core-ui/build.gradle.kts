plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose)
}

android {
    namespace = "dev.rift.ui"
    buildFeatures.compose = true
}

kotlin {
    compilerOptions {
        // Some Material 3 Expressive APIs are still experimental; opting in is harmless for the stable ones.
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
