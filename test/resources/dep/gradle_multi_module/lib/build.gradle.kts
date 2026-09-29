plugins {
    `java-library`
}

dependencies {
    implementation(libs.clojure)
    // The platform is listed, the dependency that gets its version from it is not
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.12.0"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // Not listed: a constraint is not a dependency
    constraints {
        implementation("org.clojure:java.classpath:1.0.0")
    }
}
