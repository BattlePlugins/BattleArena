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
include("module:auto-arena")
include("module:boundary-enforcer")
include("module:classes")
include("module:duels")
include("module:hologram-integration")
include("module:one-in-the-chamber")
include("module:party-integration")
include("module:placeholderapi-integration")
include("module:scoreboards")
include("module:team-colors")
include("module:team-heads")
include("module:tournaments")
include("module:vault-integration")
