import org.taumc.gradle.publishing.api.PublishChannel

plugins {
    id("org.taumc.gradle.versioning")
    id("org.taumc.gradle.publishing")
}

project.version = tau.versioning.version(rootProject.findProperty("project_base_version").toString(), rootProject.findProperty("release_channel"))
println("Celeritas: ${tau.versioning.version}")

tau.publishing.publish {
    useTauGradleVersioning()
    changelog = "Further improvements to overall system stability and other minor adjustments have been made to enhance the user experience."

    discord {
        supportAllChannelsExcluding(PublishChannel.RELEASE)

        webhookURL = providers.environmentVariable("DISCORD_WEBHOOK")
        username = "Celeritas Test Builds"
        avatarURL = "https://git.taumc.org/embeddedt/celeritas/raw/branch/stonecutter/ornithe/src/main/resources/icon.png"

        testBuildPreset("Celeritas", "https://git.taumc.org/embeddedt/celeritas")
    }

    if (System.getenv("GITEA_TOKEN") != null) {
        github("Gitea") {
            supportAllChannels()
            uploadArtifacts = false

            apiEndpoint = "https://git.taumc.org/api/v1/"

            accessToken = System.getenv("GITEA_TOKEN")
            repository = "embeddedt/celeritas"
            tagName = tau.versioning.releaseTag
        }
    }
}

// TauGradle's publish artifacts hold a reference to the Project, which can't be serialized.
tasks.named("taugradle_publish") {
    notCompatibleWithConfigurationCache("TauGradle publish artifacts reference the Project")
}
