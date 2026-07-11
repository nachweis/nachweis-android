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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Vendored, PR#369-patched wallet-core (see vendor/wallet-core-pr369/README.md).
        // Scoped to that one coordinate so nothing else resolves from the local repo.
        // This branch intentionally diverges from main, which stays on the official 0.28.1.
        maven {
            url = uri("${rootDir}/vendor/maven")
            content {
                includeModule("eu.europa.ec.eudi", "eudi-lib-android-wallet-core")
            }
        }
    }
}

rootProject.name = "nachweis"
include(":app")
