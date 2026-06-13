plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group   = "com.github.areiljan"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
    explicitApi()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines)

    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("kfinance")
                description.set("Kotlin Yahoo Finance stock support")
                url.set("https://github.com/areiljan/kfinance")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
