plugins {
    id("buildlogic.java-application-conventions")
    id("buildlogic.docker-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.guava)
}

application {
    // Define the main class for the application.
    mainClass = "com.github.lhotari.dss25.httpserver.SlowService"
}

docker {
    javaApplication {
        ports.set(listOf(8888))
    }
}
