plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.6.1"
    id("com.diffplug.spotless") version "6.25.0"
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:26.3-R0.1-SNAPSHOT")
    compileOnly("org.spigotmc:spigot:26.3-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

spotless {
    java {
        googleJavaFormat("1.28.0").aosp()
        target(
            "src/main/java/**/*.java",
            "src/legacy/java/**/*.java",
            "src/modern/java/**/*.java",
        )
        removeUnusedImports()
        formatAnnotations()
        trimTrailingWhitespace()
        toggleOffOn("// @formatter:off", "// @formatter:on")
    }
}

tasks {
    compileJava {
        dependsOn("spotlessApply")
    }

    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        // relocate("com.fasterxml.jackson", "me.pintoadmin.fabricmoddownloader.shaded.com.fasterxml.jackson")
        // relocate("org.yaml.snakeyaml", "me.pintoadmin.fabricmoddownloader.shaded.org.yaml.snakeyaml")
        archiveClassifier.set("")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
