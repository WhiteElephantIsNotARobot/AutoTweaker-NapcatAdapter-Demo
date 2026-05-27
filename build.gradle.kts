plugins {
    kotlin("jvm") version "2.3.21"
    application
}

group = "io.github.autotweaker.demo.adapter.napcat"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/AutoTweaker/core")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("io.github.autotweaker:api:0.1.0-alpha.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.slf4j:slf4j-api:2.0.18")
}

application {
    mainClass.set("io.github.autotweaker.demo.adapter.napcat.MainKt")
}
