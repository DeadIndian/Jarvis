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

rootProject.name = "Jarvis"

include(":app")
include(":core")
include(":input")
include(":intent")
include(":planner")
include(":execution")
include(":skills")
include(":memory")
include(":llm")
include(":output")
include(":android")
include(":logging")
