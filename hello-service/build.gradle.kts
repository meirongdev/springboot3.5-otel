plugins {
    java
    jacoco
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":shared"))
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner")
    testImplementation(project(path = ":greeting-service", configuration = "stubs"))
    testImplementation(project(path = ":user-service", configuration = "stubs"))
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
