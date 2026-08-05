plugins {
    id("java")
}

allprojects {
    apply {
        plugin("java")
        plugin("java-library")
    }

    group = "org.battleplugins"
    version = "4.0.5-SNAPSHOT"

    repositories {
        mavenCentral()

        // Spigot
        maven("https://hub.spigotmc.org/nexus/content/groups/public/")

        // Paper, Velocity
        maven("https://repo.papermc.io/repository/maven-public")

        // WorldEdit
        maven("https://maven.enginehub.org/repo/")
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    dependencies {
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        testImplementation(platform("org.junit:junit-bom:5.10.2"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.mockito:mockito-core:5.+")
    }

    tasks.test {
        useJUnitPlatform()
    }
}
