plugins {
    java
    id("net.minecrell.licenser") version "0.3"
}

java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = "destroystokyo-repo"
        setUrl("https://repo.destroystokyo.com/repository/maven-public/")
    }

    maven {
        name = "spongepowered-repo"
        setUrl("https://repo.spongepowered.org/maven")
    }

    maven {
        name = "mikroskeem-repo"
        setUrl("https://repo.wut.ee/repository/mikroskeem-repo")
    }
}

dependencies {
    compileOnly("eu.mikroskeem:orion.api:0.0.3-SNAPSHOT")
    compileOnly("com.destroystokyo.paper:paper:1.12.2-R0.1-SNAPSHOT")
}

license {
    sourceSets = setOf(java.sourceSets["main"])
    header = rootProject.file("etc/HEADER")
}
