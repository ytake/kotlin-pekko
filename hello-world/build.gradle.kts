plugins {
    kotlin("jvm") version "2.0.10"
    application
    id("com.diffplug.spotless") version "6.25.0"
}

apply(plugin = "java")
apply(plugin = "kotlin")

group = "com.github.ytake.pekko"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.pekko:pekko-actor-typed_2.13:1.1.1")
    implementation("ch.qos.logback:logback-classic:1.5.8")
    testImplementation(kotlin("test"))
}

application {
    mainClass = "HelloWorldMain"
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(15)
}

spotless {
    java {
        googleJavaFormat()
    }
    kotlin {
        target("**/*.kt")
        ktlint()
            .editorConfigOverride(mapOf(
                "experimental" to "true",
                "indent_size" to "2",
                "trim_trailing_whitespace" to "true",
                "max_line_length" to "120"
            ))
        trimTrailingWhitespace()
        endWithNewline()
    }
}