plugins {
    `java-library`
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.9"
}

group = "com.drzenovka.heavyswing"
version = "1.0.3"
base { archivesName.set("HeavySwing") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(8)) }
minecraft {
    mcVersion.set("1.7.10")
    username.set("Developer")
}
repositories {
    mavenCentral()
    maven("https://nexus.gtnewhorizons.com/repository/public/")
}
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
