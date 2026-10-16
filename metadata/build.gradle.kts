plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.rift.metadata"
}

dependencies {
    api(project(":core-model"))
}
