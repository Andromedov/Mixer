val paperApiVersion = providers.gradleProperty("paperApiVersion")
    .orElse("1.21.4-R0.1-SNAPSHOT")

dependencies {
    compileOnlyApi("io.papermc.paper:paper-api:${paperApiVersion.get()}")
}
