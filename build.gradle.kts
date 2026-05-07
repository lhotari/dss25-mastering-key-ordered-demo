import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    alias(libs.plugins.versions)
    alias(libs.plugins.version.catalog.update)
    alias(libs.plugins.spotless)
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    rejectVersionIf {
        val v = candidate.version
        v.contains("alpha") || v.contains("beta") || v.contains("rc")
    }
}

versionCatalogUpdate {
    keep {
        keepUnusedVersions = true
    }
}
