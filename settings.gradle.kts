pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "clawapk"

include(":app")
include(":core:domain")
include(":core:common")
include(":core:data")
include(":libs:websocket")
include(":libs:tts")
include(":libs:stt")
include(":feature:chat")
include(":feature:notifications")
