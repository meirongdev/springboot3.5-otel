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
    implementation("org.springframework.kafka:spring-kafka")
    // JDBC tracing: wraps DataSource with ObservationProxyDataSource to generate db.query spans
    implementation("net.ttddyy.observation:datasource-micrometer-spring-boot:1.0.6")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.cloud:spring-cloud-starter-contract-verifier")
    testImplementation("io.rest-assured:spring-mock-mvc")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.awaitility:awaitility")
}
