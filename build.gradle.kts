plugins {
    id("java")
}

allprojects {
    apply {
        plugin("java")
        plugin("java-library")
    }

    group = "org.battleplugins"
    version = "4.0.3-SNAPSHOT"

    repositories {
        maven("https://repo.papermc.io/repository/maven-public")
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}
