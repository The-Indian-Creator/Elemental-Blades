plugins {
    java
}

group = "com.elementalblades"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper 26.x uses the <mcver>.build.<n> version scheme. Keep ".build." so 26.3.+ never pulls a breaking hotfix.
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
}

java {
    // Paper 26.x requires Java 25 (Gradle downloads it automatically if missing).
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    jar {
        archiveFileName.set("ElementalBlades-${project.version}.jar")
    }
}
