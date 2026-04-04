plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.springframework.cloud.contract") version "4.1.4"
}

contracts {
    contractsDslDir.set(file("src/test/resources/contracts"))
    baseClassForTests.set("com.example.user.UserServiceContractTest")
}

dependencies {
    implementation(project(":shared"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-verifier")
    testImplementation("io.rest-assured:spring-mock-mvc")
}
