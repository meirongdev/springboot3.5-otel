import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    base
    id("org.springframework.boot") version "3.5.12" apply false
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.4.0"
    id("net.ltgt.errorprone") version "5.1.0" apply false
    id("jacoco-report-aggregation")
}

allprojects {
    group = "com.example"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.12")
    }
}

dependencies {
    jacocoAggregation(project(":shared"))
    jacocoAggregation(project(":hello-service"))
    jacocoAggregation(project(":user-service"))
    jacocoAggregation(project(":greeting-service"))
}

reporting {
    reports {
        create<JacocoCoverageReport>("testCodeCoverageReport") {
            testSuiteName.set("test")
        }
    }
}

spotless {
    java {
        target("**/*.java")
        googleJavaFormat("1.34.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    // Force OpenTelemetry 1.49.0 to match logback-appender 2.13.0-alpha requirements
    // Spring Boot 3.5.12 manages 1.47.0 which lacks the setException method
    pluginManager.withPlugin("io.spring.dependency-management") {
        extensions.configure<DependencyManagementExtension> {
            dependencies {
                dependency("io.opentelemetry:opentelemetry-api:1.49.0")
                dependency("io.opentelemetry:opentelemetry-sdk:1.49.0")
                dependency("io.opentelemetry:opentelemetry-sdk-common:1.49.0")
                dependency("io.opentelemetry:opentelemetry-sdk-trace:1.49.0")
                dependency("io.opentelemetry:opentelemetry-sdk-metrics:1.49.0")
                dependency("io.opentelemetry:opentelemetry-sdk-logs:1.49.0")
                dependency("io.opentelemetry:opentelemetry-exporter-otlp:1.49.0")
                dependency("io.opentelemetry:opentelemetry-exporter-otlp-common:1.49.0")
                dependency("io.opentelemetry:opentelemetry-exporter-common:1.49.0")
                dependency("io.opentelemetry:opentelemetry-exporter-sender-okhttp:1.49.0")
                dependency("io.opentelemetry:opentelemetry-context:1.49.0")
                dependency("io.opentelemetry:opentelemetry-extension-trace-propagators:1.49.0")
            }
        }
    }

    pluginManager.withPlugin("java") {
        apply(plugin = "net.ltgt.errorprone")

        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(25)
            }
        }

        dependencies {
            add("errorprone", "com.google.errorprone:error_prone_core:2.48.0")
        }

        tasks.withType(JavaCompile::class.java).configureEach {
            options.errorprone {
                disableWarningsInGeneratedCode.set(true)
            }
        }

        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }
    }

    pluginManager.withPlugin("io.spring.dependency-management") {
        extensions.configure<DependencyManagementExtension> {
            imports {
                mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
                mavenBom("org.springframework.cloud:spring-cloud-dependencies:2024.0.0")
                mavenBom("io.opentelemetry:opentelemetry-bom:1.49.0")
                mavenBom("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:2.13.0-alpha")
            }
        }
    }

    pluginManager.withPlugin("org.springframework.cloud.contract") {
        val stubs by configurations.creating

        tasks.named("assemble") {
            dependsOn(tasks.named("verifierStubsJar"))
        }

        configurations["stubs"].outgoing.artifact(tasks.named("verifierStubsJar"))
    }
}

// Service projects that need full JaCoCo coverage verification
listOf(":hello-service", ":user-service", ":greeting-service").forEach { projectName ->
    project(projectName) {
        pluginManager.apply("jacoco")

        afterEvaluate {
            extensions.configure<JacocoPluginExtension> {
                toolVersion = "0.8.13"
            }

            tasks.withType(Test::class.java).configureEach {
                finalizedBy(tasks.named("jacocoTestReport"))
            }

            tasks.named("jacocoTestReport") {
                dependsOn(tasks.withType(Test::class.java))
            }

            tasks.withType(JacocoCoverageVerification::class.java).configureEach {
                dependsOn(tasks.named("jacocoTestReport"))
                violationRules {
                    rule { limit { minimum = "0.80".toBigDecimal() } }
                }
            }

            tasks.named("check") {
                dependsOn(tasks.withType(JacocoCoverageVerification::class.java))
            }
        }
    }
}
