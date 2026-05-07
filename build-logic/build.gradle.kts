plugins {
    // Support convention plugins written in Kotlin DSL. Convention plugins are build
    // scripts in 'src/main/kotlin' that automatically become available as plugins in
    // the main build.
    `kotlin-dsl`
}

repositories {
    // Use the plugin portal to apply community plugins in convention plugins.
    gradlePluginPortal()
}

dependencies {
    implementation("com.bmuschko:gradle-docker-plugin:9.4.0")
    // Expose the version catalog accessor (`libs`) to precompiled Kotlin script plugins.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
