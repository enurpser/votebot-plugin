import org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension

plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
allprojects {
    version = "5.4.1"
    group = "space.votebot"

    repositories {
        mavenCentral()
    }
}

subprojects {
    afterEvaluate {
        configure<KotlinTopLevelExtension> {
            jvmToolchain(22)
        }
    }
}
