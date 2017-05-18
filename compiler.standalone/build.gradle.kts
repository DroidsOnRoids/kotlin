
apply { plugin("kotlin") }

dependencies {
    compile(project(":compiler"))
    compile(project(":compiler:cli"))
    compile(ideaSdkCoreDeps(*(rootProject.extra["ideaCoreSdkJars"] as Array<String>)))
    compile(commonDep("org.fusesource.jansi", "jansi"))
    compile(commonDep("jline"))
}

configureKotlinProjectSources(
        "compiler/daemon/src",
        "compiler/conditional-preprocessor/src",
        "compiler/incremental-compilation-impl/src",
        sourcesBaseDir = rootDir)
configureKotlinProjectNoTests()

fixKotlinTaskDependencies()
