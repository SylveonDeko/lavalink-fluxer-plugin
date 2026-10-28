plugins {
    java
    alias(libs.plugins.lavalink)
}

group = "tech.mewdeko"
version = "0.1.1"

lavalinkPlugin {
    name = "fluxer-plugin"
    apiVersion = libs.versions.lavalink.api
    serverVersion = libs.versions.lavalink.server
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 17
    }
}

dependencies {
    compileOnly(libs.spring.websocket)
    compileOnly(libs.jakarta.servlet)
}
