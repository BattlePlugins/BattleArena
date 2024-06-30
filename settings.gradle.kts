rootProject.name = "BattleArena"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        mavenCentral()

        // Spigot
        maven("https://hub.spigotmc.org/nexus/content/groups/public/")

        // Paper, Velocity
        maven("https://repo.papermc.io/repository/maven-public")
    }
}

// Base plugin
include("plugin")

// Default modules
include("module:arena-restoration")
include("module:boundary-enforcer")
include("module:classes")
include("module:join-messages")
include("module:team-heads")
include("module:tournaments")
include("module:vault-integration")