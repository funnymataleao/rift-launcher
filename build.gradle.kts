import com.android.build.api.dsl.CommonExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}

// Shared defaults for every module: module build files only declare what is specific to them.
subprojects {
    val catalog = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    fun sdk(alias: String) = catalog.findVersion(alias).get().requiredVersion.toInt()

    val androidDefaults: CommonExtension.() -> Unit = {
        compileSdk = sdk("compileSdk")
        defaultConfig.minSdk = sdk("minSdk")
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }
    plugins.withId("com.android.application") {
        extensions.configure<CommonExtension>("android", androidDefaults)
        extensions.findByType<KotlinAndroidProjectExtension>()?.compilerOptions?.jvmTarget?.set(JvmTarget.JVM_17)
    }
    plugins.withId("com.android.library") {
        extensions.configure<CommonExtension>("android", androidDefaults)
        extensions.findByType<KotlinAndroidProjectExtension>()?.compilerOptions?.jvmTarget?.set(JvmTarget.JVM_17)
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        extensions.configure<KotlinJvmProjectExtension> { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
    }
}
