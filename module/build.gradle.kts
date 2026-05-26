subprojects {
    dependencies {
        compileOnlyApi(project(":plugin"))

        testRuntimeOnly(rootProject.libs.paper.api)
        testRuntimeOnly(project(":plugin"))
    }

    tasks.jar {
        from("src/main/java/resources") {
            include("*")
        }

        archiveFileName.set("${project.name}.jar")
        archiveClassifier.set("")
    }
}
