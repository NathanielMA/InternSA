import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
}

group = "me.nated"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {
    implementation("org.lwjgl.lwjgl", "lwjgl", "2.9.3")
    implementation("org.lwjgl.lwjgl","lwjgl_util", "2.9.3")
}