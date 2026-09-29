rootProject.name = "gradle_multi_module"

dependencyResolutionManagement {
    repositories {
        maven {
            name = "clojars"
            url = uri("https://repo.clojars.org")
        }
    }
}

include("lib", "app", "groovy-lib", "scala-lib")
