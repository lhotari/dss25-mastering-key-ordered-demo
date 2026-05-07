plugins {
    id("com.bmuschko.docker-java-application")
}

docker {
    javaApplication {
        baseImage.set("amazoncorretto:21-alpine3.22")
        images.set(listOf("${project.name}:${project.version}", "${project.name}:latest"))
        jvmArgs.set(
            listOf(
                "-Xms200m", "-Xmx200m",
                "-XX:+UseZGC", "-XX:+ZGenerational", "-XX:+AlwaysPreTouch"
            )
        )
    }
}
