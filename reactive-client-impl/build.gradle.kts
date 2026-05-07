plugins {
    id("buildlogic.java-application-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.pulsar.reactive.client)
    implementation(libs.reactor.netty)
}

application {
    // Define the main class for the application.
    mainClass = "com.github.lhotari.dss25.reactiveclient.PulsarReactiveClientApp"
}
