plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.rift.providers.pcstream"
}

dependencies {
    implementation(project(":providers:api"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
