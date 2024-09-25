plugins {
    kotlin("jvm") version "2.0.10"
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

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(15)
}