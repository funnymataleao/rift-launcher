pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "rift-launcher"

include(":app")
include(":core-model")
include(":core-ui")
include(":library")
include(":metadata")
include(":assistant")
include(":providers:api")
include(":providers:android")
include(":providers:pcstream")
include(":providers:gamenative")
