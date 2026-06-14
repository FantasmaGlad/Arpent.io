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
    
    // Read .env file for Mapbox download credentials
    val envFile = java.io.File(rootDir, ".env")
    val envProperties = java.util.Properties()
    if (envFile.exists()) {
        envFile.inputStream().use { envProperties.load(it) }
    }
    val mapboxSecretToken = envProperties.getProperty("MAPBOX_SECRET_TOKEN") ?: ""
    val mapboxPublicToken = envProperties.getProperty("MAPBOX_PUBLIC_TOKEN") ?: ""
    val downloadToken = if (mapboxSecretToken.isNotEmpty()) mapboxSecretToken else mapboxPublicToken

    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                username = "mapbox"
                password = downloadToken
            }
        }
    }
}

rootProject.name = "Arpent.io"
include(":app")
 