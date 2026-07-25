import org.gradle.api.JavaVersion

val currentJavaMajorVersion = JavaVersion.current().majorVersion.toInt()
check(currentJavaMajorVersion >= 25) {
    "JDK 25 or newer is required. Current version: ${JavaVersion.current()}"
}

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
        // Local patched miuix-ui (softer TopAppBar title crossfade)
        exclusiveContent {
            forRepository {
                maven {
                    name = "miuixLocal"
                    url = uri(rootDir.resolve("third_party/miuix-local-maven"))
                }
            }
            filter {
                includeModule("top.yukonga.miuix.kmp", "miuix-ui-android")
            }
        }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}


rootProject.name = "OnlyPlayer"

include(":app")
include(":core:common")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:domain")
include(":core:media")
include(":core:model")
include(":core:ui")
include(":feature:player")
include(":feature:settings")
include(":feature:videopicker")
