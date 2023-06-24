import dev.schlaubi.mikbot.gradle.mikbot
import org.gradle.api.Project
import java.io.ByteArrayOutputStream

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
    alias(libs.plugins.mikbot)
    alias(libs.plugins.buildconfig)
}

dependencies {
    api(projects.common)
    implementation(projects.chartServiceClient)
    implementation(libs.java.string.similarity)
    ksp(libs.kordex.processor)
    optionalPlugin(mikbot(libs.mikbot.gdpr))
}

mikbotPlugin {
    description = "Plugin adding VoteBot functionality"
    bundle = "votebot"
    pluginId = "votebot"
    provider = "votebot.space"
    license = "MIT"
}

buildConfig  {
    packageName = "space.votebot"
    className = "VoteBotInfo"
    buildConfigField("String", "VERSION", "\"${project.version}\"")
    buildConfigField("String", "BRANCH", "\"${project.getGitBranch()}\"")
    buildConfigField("String", "COMMIT", "\"${project.getGitCommit()}\"")
}

sourceSets {
    main {
        java {
            srcDir(file("$buildDir/generated/ksp/main/kotlin/"))
        }
    }
}

tasks {
    assembleBot {
        val mikbotVersion = libs.versions.mikbot.get()
        bundledPlugins.addAll("gdpr@$mikbotVersion", "database-i18n@$mikbotVersion")
    }
}

fun Project.getGitCommit(): String {
    return execCommand(arrayOf("git", "rev-parse", "--short", "HEAD"))
        ?: System.getenv("GITHUB_SHA") ?: "<unknown>"
}

fun Project.getGitBranch(): String {
    return execCommand(arrayOf("git", "rev-parse", "--abbrev-ref", "HEAD")) ?: "unknown"
}

fun Project.execCommand(command: Array<String>): String? {
    return try {
        ByteArrayOutputStream().use { out ->
            exec {
                commandLine(command.asIterable())
                standardOutput = out
            }
            out.toString().trim()
        }
    } catch (e: Throwable) {
        logger.warn("An error occurred whilst executing a command", e)
        null
    }
}
