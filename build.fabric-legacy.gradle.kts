plugins {
    id("net.fabricmc.fabric-loom-remap")
}

val mc = stonecutter.current.version
val modId = property("mod.id") as String
val javaVer = if (mc.startsWith("1.20")) 17 else 21

version = "${property("mod.version")}+$mc-fabric"
group = property("mod.group") as String
base.archivesName = modId

repositories {
    maven("https://maven.terraformersmc.com/") {
        name = "TerraformersMC"
        content { includeGroup("com.terraformersmc") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    mappings(loom.officialMojangMappings())
    annotationProcessor("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    // optional: Mod Menu's settings button; compile only, not shipped or required
    modCompileOnly("com.terraformersmc:modmenu:${property("deps.modmenu")}") { isTransitive = false }

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    mixin.useLegacyMixinAp = true
    runs.named("client") {
        client()
        runDir = "run"
        programArgs("--username", "Builder")
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(javaVer)
    targetCompatibility = JavaVersion.toVersion(javaVer)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVer
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "mc" to mc,
        "name" to project.property("mod.name"),
        "description" to project.property("mod.description"),
        "author" to project.property("mod.author"),
        "homepage" to project.property("mod.homepage"),
        "fabric_loader" to project.property("deps.fabric_loader"),
        "mc_range" to ((findProperty("deps.mc_range") as String?) ?: "~$mc"),
        "java" to javaVer.toString(),
    )
    inputs.properties(props)
    exclude("META-INF/mods.toml", "META-INF/neoforge.mods.toml")
    filesMatching("fabric.mod.json") { expand(props) }
    if (javaVer < 21) filesMatching("*.mixins.json") { filter { it.replace("JAVA_21", "JAVA_$javaVer") } }
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE"))
    // compile-time stand-ins for Sodium classes (src/main/java/net/caffeinemc): the real ones come from Sodium
    exclude("net/caffeinemc/**")
}

extra["mcVersion"] = mc
apply(from = rootProject.file("../../tools/Backport/renames.gradle.kts"))
