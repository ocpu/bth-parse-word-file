import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.Coroutines

plugins {
  kotlin("jvm") version "1.2.51"
}

group = "me.ocpu"
version = "0.0.0"

repositories {
  mavenCentral()
}

dependencies {
  compile(kotlin("stdlib-jdk8"))
  compile(kotlin("reflect"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.30.2")
  compile("org.docx4j:docx4j:6.0.1")
  compile("mysql:mysql-connector-java:8.0.11")
  compile("no.tornado:tornadofx:1.7.17")
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "1.8"
}
kotlin {
  experimental.coroutines = Coroutines.ENABLE
}