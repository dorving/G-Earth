import org.gradle.internal.os.OperatingSystem
import org.gradle.launcher.daemon.protocol.Build

plugins {
    id("org.beryx.runtime") version "1.12.5"
    id("org.openjfx.javafxplugin") version "0.0.11"
    `java-library`
    `maven-publish`
}

description = "G-Earth"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

/**
 * TODO: move dependency version declarations to different gradle file
 */
dependencies {
    implementation("com.github.dorving:G-Wasm:minimal-SNAPSHOT")
    implementation("at.favre.lib:bytes:1.5.0")
    implementation("com.github.tulskiy:jkeymaster:1.3")
    implementation("com.github.ganskef:littleproxy-mitm:1.1.0")
    implementation("commons-io:commons-io:2.10.0")
    implementation("javax.websocket:javax.websocket-api:1.1")
    implementation("org.apache.maven:maven-artifact:3.6.3")
    implementation("org.eclipse.jetty:jetty-server:9.4.43.v20210629")
    implementation("org.eclipse.jetty.websocket:javax-websocket-server-impl:9.4.43.v20210629")   {
        exclude("javax.websocket", "javax.websocket-client-api")
    }
    implementation("org.eclipse.jetty:jetty-http:9.4.43.v20210629")
    implementation("org.fxmisc.richtext:richtextfx:0.10.5")
    implementation("org.json:json:20190722")
    implementation("org.jsoup:jsoup:1.14.2")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("ch.qos.logback:logback-core:1.2.3")
    implementation("com.alibaba:dns-cache-manipulator:1.8.0-RC1")

}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

javafx {
    version = "17.0.2"
    modules(
        "javafx.base",
        "javafx.controls",
        "javafx.fxml",
        "javafx.graphics",
        "javafx.media",
        "javafx.swing",
        "javafx.web"
    )
}

application {
    mainClass.set("gearth.GEarthLauncher")
    applicationName = "G-Earth"
}
val copyGMem by tasks.registering(Copy::class) {
    from(file("$projectDir/src/main/resources/build/mac/G-Mem"))
    into("$buildDir/classes/java")
    shouldRunAfter(tasks.getByName("jar"))
}

tasks.getByName("assemble") {

    dependsOn(copyGMem)
}

val copyGMem by tasks.registering(Copy::class) {
    from(file("$projectDir/src/main/resources/build/mac/G-Mem"))
    into("$buildDir/classes/java")
    shouldRunAfter(tasks.getByName("jar"))
}

tasks.getByName("assemble") {

    dependsOn(copyGMem)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "karth.gearth"
            artifactId = "gearth"
            version = "1.5.2"
            from(components["java"])
        }
    }
}

runtime {
    addModules(
        "java.datatransfer", "java.desktop", "java.prefs",
        "java.logging", "java.naming", "java.net.http",
        "java.sql", "java.scripting", "java.xml",
        "jdk.crypto.ec", "jdk.jfr", "jdk.jsobject",
        "jdk.unsupported", "jdk.unsupported.desktop", "jdk.xml.dom"
    )
    launcher {
        noConsole = true
    }
    jpackage {

        val currentOs = OperatingSystem.current()

        val imgType = when {
            currentOs.isWindows -> "ico"
            currentOs.isMacOsX -> "icns"
            else -> "png"
        }

        // TODO: add support for dark-theme icon, maybe depending on OS theme.
        imageOptions.addAll(arrayOf("--icon", "src/main/resources/gearth/ui/themes/G-Earth/logo.$imgType"))

        if (currentOs.isWindows) {
            installerOptions.addAll(
                listOf(
                    "--win-per-user-install",
                    "--win-dir-chooser",
                    "--win-menu"
                )
            )
        }
    }
}

tasks.jpackageImage {
    doLast {
        val os = OperatingSystem.current()
        val outPath = when {
            os.isWindows -> project.name
            os.isMacOsX -> "${project.name}.app/Contents"
            else -> "${project.name}/lib"
        }
        copy {
            val buildResourcesPath = "src/main/resources/build"
            when {
                os.isWindows -> {
                    /*
                    TODO: differentiate between 32bit and 64bit windows.
                     */
                    from("$buildResourcesPath/windows/64bit")
                    include("G-Mem.exe")
                }
                os.isMacOsX -> {
                    from("$buildResourcesPath/mac")
                    /*
                     * The`g_mem_mac` executable is generated by a modified version of the G-Mem program.
                     *
                     * Which can be found here: https://github.com/dorving/g_mem_mac
                     */
                    include("G-Mem")
                }
            }
            into("$buildDir/jpackage/$outPath/app")
        }
    }
}
