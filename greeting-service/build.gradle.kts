plugins {
    java
    jacoco
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.springframework.cloud.contract") version "4.1.4"
    id("maven-publish")
}

contracts {
    contractsDslDir.set(file("src/test/resources/contracts"))
    baseClassForTests.set("com.example.greeting.GreetingServiceContractTest")
}

val stubs by configurations.creating

artifacts {
    add("stubs", tasks.named("verifierStubsJar"))
}

dependencies {
    implementation(project(":shared"))
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "com.vaadin.external.google", module = "android-json")
    }
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-verifier")
    testImplementation("io.rest-assured:spring-mock-mvc")
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
