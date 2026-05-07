import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    // Apply the java Plugin to add support for Java.
    java
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

val libs = the<LibrariesForLibs>()

dependencies {
    constraints {
    }
    implementation(platform(libs.log4j.bom))
    implementation(libs.bundles.log4j)
}

testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit.jupiter)
        }
    }
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
