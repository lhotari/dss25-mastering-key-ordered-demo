plugins {
    id("buildlogic.java-application-conventions")
}

dependencies {
    implementation(project(":common"))
}

application {
    // Define the main class for the application.
    mainClass = "com.github.lhotari.dss25.listener.PulsarListenerApp"
}
