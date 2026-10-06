plugins {
    id("java-library")
    id("net.neoforged.moddev") version "2.0.44-beta"
}

operator fun String.invoke(): String {
    return (rootProject.properties[this] as String?) ?: throw IllegalArgumentException("Couldn't find '$this' property")
}

version = "mod.version"()
group = "mod.group_id"()

base {
    archivesName = "mod.artifact_name"() + "-" + "mod.minecraft"()
}

repositories {
    maven {
        url = uri("maven_url"())
        name = "Aliyun"
    }
    mavenCentral()
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

neoForge {
    version = "mod.neo_version"()

    parchment {
        mappingsVersion = "mappings.version"()
        minecraftVersion = "mod.minecraft"()
    }

    runs {
        register("client") {
            client()
        }

        register("server") {
            server()
            programArguments.add("--nogui")
        }
    }

    mods {
        register("mod.id"()) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // Kaleidoscope Cookery is the addon target: compiled against it, loaded beside it in dev runs.
    // The jar is not redistributed with this project (see .gitignore), users install it separately.
    compileOnly(files("libs/kaleidoscopecookery-${"deps.kaleidoscope_cookery"()}.jar"))
    runtimeOnly(files("libs/kaleidoscopecookery-${"deps.kaleidoscope_cookery"()}.jar"))
}
