plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(libs.pulsar.client.all)
    api(libs.resilience4j.retry)
}
