rootProject.name = "PintoMurderMystery"
if (providers.gradleProperty("devBuild").isPresent) {
    rootProject.buildFileName = "dev.build.gradle.kts"
}