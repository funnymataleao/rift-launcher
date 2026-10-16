plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.rift.assistant"
}

dependencies {
    api(project(":core-model"))
}
