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
rootProject.name = "Bandeja"
// app = reloj (Wear OS), mobile = móvil (Android), shared = rutina, datos y sincronización
include(":app", ":mobile", ":shared")
