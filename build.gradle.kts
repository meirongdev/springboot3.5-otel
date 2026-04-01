import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

plugins {
    base
    id("org.springframework.boot") version "3.5.0" apply false
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
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.0")
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
        googleJavaFormat("1.28.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    pluginManager.withPlugin("java") {
        apply(plugin = "net.ltgt.errorprone")
        apply(plugin = "jacoco")

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
            systemProperty(
                "pact.rootDir",
                rootProject.layout.buildDirectory
                    .dir("pacts")
                    .get()
                    .asFile.absolutePath,
            )
        }

        extensions.configure<JacocoPluginExtension> {
            toolVersion = "0.8.13"
        }
    }

    pluginManager.withPlugin("io.spring.dependency-management") {
        extensions.configure<DependencyManagementExtension> {
            imports {
                mavenBom("org.testcontainers:testcontainers-bom:1.21.3")
            }
        }
    }
}

gradle.projectsEvaluated {
    project(":user-service").tasks.named("test") {
        dependsOn(project(":hello-service").tasks.named("test"))
    }

    project(":greeting-service").tasks.named("test") {
        dependsOn(project(":hello-service").tasks.named("test"))
    }
}
