pluginManagement {
    // Include 'plugins build' to define convention plugins.
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "dss25-demo"
include("generator", "common", "http-server")
include("pulsar-listener", "reactive-client-impl")
