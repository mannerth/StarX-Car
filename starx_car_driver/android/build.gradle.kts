allprojects {
    repositories {
        // Mirrors keep Android dependency resolution available when dl.google.com is unreachable.
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://repo.huaweicloud.com/repository/maven/")
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
