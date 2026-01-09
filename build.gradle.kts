// Copyright (c) 2025 Axel Howind
//
// This file is part of Keystore Manager.
//
// Keystore Manager is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License version 3 as published
// by the Free Software Foundation.
//
// Keystore Manager is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with Keystore Manager. If not, see <https://www.gnu.org/licenses/>.
//
// SPDX-License-Identifier: GPL-3.0-only

import com.adarshr.gradle.testlogger.theme.ThemeType
import com.dua3.cabe.processor.Configuration
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.internal.extensions.stdlib.toDefaultLowerCase

plugins {
    id("java")
    id("signing")
    id("idea")
    id("application")
    alias(libs.plugins.jdk)
    alias(libs.plugins.graalvm)
    alias(libs.plugins.jlink)
    alias(libs.plugins.versions)
    alias(libs.plugins.test.logger)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.cabe)
    alias(libs.plugins.forbiddenapis)
}

/////////////////////////////////////////////////////////////////////////////
object Meta {
    const val GROUP = "com.dua3.app.keystoremanager"
    const val SCM = "https://github.com/xzel23/keystoremnager.git"
    const val REPO = "public"
    const val LICENSE_NAME = "GPL-3.0-only"
    const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.txt"
    const val DEVELOPER_ID = "axh"
    const val DEVELOPER_NAME = "Axel Howind"
    const val DEVELOPER_EMAIL = "axh@dua3.com"
    const val ORGANIZATION_NAME = "dua3"
    const val ORGANIZATION_URL = "https://www.dua3.com"
}
/////////////////////////////////////////////////////////////////////////////

application {
    mainClass = "com.dua3.app.keystoremanager.Main"
    applicationDefaultJvmArgs = listOf("--enable-native-access=javafx.graphics,ALL-UNNAMED")
}

graalvmNative {
    binaries {
        all {
            resources.autodetect()
            this.javaLauncher = jdk.getJavaLauncher(project)
        }
        named("main") {
            imageName.set("keystoremanager")
            mainClass.set("com.dua3.app.keystoremanager.Main")
            buildArgs.addAll(
                "--enable-native-access=ALL-UNNAMED",
                "--enable-native-access=javafx.graphics"
            )
        }
    }
}

// Configure Badass JLink to create a custom runtime image and jpackaged app
jlink {
    javaHome = jdk.jdkHome

    // Module name is inferred from module-info.java (open module keystoremanager)
    imageName.set("KeystoreManager")

    // Keep image reasonably small
    addOptions(
        "--strip-debug",
        "--no-header-files",
        "--no-man-pages",
        // required because some dependencies (e.g., BouncyCastle PKIX) ship as signed modular JARs
        "--ignore-signing-information"
    )

    launcher {
        name = "keystoremanager"
        // mainClass is taken from the application plugin; set explicitly for clarity
        mainClass.set("com.dua3.app.keystoremanager.Main")
        jvmArgs = listOf("-Dprism.allowhidpi=true", "--enable-native-access=javafx.graphics,ALL-UNNAMED")
    }

    // jpackage configuration for native bundles and app image
    jpackage {
        // Use a clean, OS-agnostic default; users may override with -PinstallerType=<dmg|pkg|msi|exe|deb|rpm>
        vendor = "dua3"
        // jpackage requires a numeric version; strip qualifiers
        val ver = (project.version as String).replace(Regex("[-.](SNAPSHOT|ALPHA|BETA|RC).*", RegexOption.IGNORE_CASE), "")
        appVersion = if (ver.isNotBlank()) ver else "0.0.0"

        // Always produce an app image; installers are optional depending on OS/flags
        imageName = "KeystoreManager"

        // Common runtime options
        jvmArgs = listOf("-Dprism.allowhidpi=true", "--enable-native-access=javafx.graphics,ALL-UNNAMED")

        // Users can pass platform-specific options on the command line

        // Use platform-appropriate icon from the data/ folder at project root
        val os = org.gradle.internal.os.OperatingSystem.current()
        val iconFile = when {
            os.isMacOsX -> project.file("data/logo.icns")
            os.isWindows -> project.file("data/logo.ico")
            else -> project.file("data/logo.png") // use PNG
        }
        if (iconFile.exists()) {
            // Set icon for the app image and for the installer, when created
            imageOptions = listOf("--icon", iconFile.absolutePath)
            installerOptions = listOf("--icon", iconFile.absolutePath)
        }

        // Conditional code signing options supplied via -P properties (CI only)
        // macOS signing
        val macSign = (project.findProperty("mac.sign") as String?)?.toBoolean() == true
        if (macSign) {
            val identity = (project.findProperty("mac.identity") as String?)?.trim().orEmpty()
            if (identity.isNotEmpty()) {
                installerOptions.addAll(listOf("--mac-sign"))
                installerOptions.addAll(listOf("--mac-signing-key-user-name", identity))
                val keychain = (project.findProperty("mac.keychain") as String?)?.trim()
                if (!keychain.isNullOrEmpty()) {
                    installerOptions.addAll(listOf("--mac-signing-keychain", keychain))
                }
            }
        }

        // Windows signing
        val winSign = (project.findProperty("win.sign") as String?)?.toBoolean() == true
        if (winSign) {
            val ks = (project.findProperty("win.keystore") as String?)?.trim().orEmpty()
            val ksp = (project.findProperty("win.storepass") as String?)?.trim().orEmpty()
            val alias = (project.findProperty("win.alias") as String?)?.trim().orEmpty()
            if (ks.isNotEmpty() && ksp.isNotEmpty() && alias.isNotEmpty()) {
                installerOptions.addAll(listOf(
                        "--win-sign",
                        "--win-signing-key-store", ks,
                        "--win-signing-key-store-pass", ksp,
                        "--win-signing-key-store-type", "pkcs12",
                        "--win-signing-key-alias", alias
                    )
                )
                val signOpts = (project.findProperty("win.signingOptions") as String?)?.trim()
                if (!signOpts.isNullOrEmpty()) {
                    // pass additional options to the signtool invocation (e.g. timestamp server)
                    installerOptions.addAll(listOf("--win-signing-options", signOpts))
                }
            }
        }
    }
}

dependencies {
    implementation(rootProject.libs.dua3.utility)
    implementation(rootProject.libs.dua3.utility.fx)
    implementation(rootProject.libs.dua3.utility.fx.controls)
    implementation(rootProject.libs.log4j.api)
    implementation(rootProject.libs.bouncycastle.provider)
    implementation(rootProject.libs.bouncycastle.pkix)

    testImplementation(platform(rootProject.libs.junit.bom))
    testImplementation(rootProject.libs.junit.jupiter.api)
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
    testRuntimeOnly(rootProject.libs.junit.jupiter.engine)
}

// --- configure all project ----
project.version = rootProject.libs.versions.projectVersion.get()

fun isDevelopmentVersion(versionString: String): Boolean {
    val v = versionString.toDefaultLowerCase()
    val markers = listOf("snapshot", "alpha", "beta")
    for (marker in markers) {
        if (v.contains("-$marker") || v.contains(".$marker")) {
            return true
        }
    }
    return false
}

val isReleaseVersion = !isDevelopmentVersion(project.version.toString())
val isSnapshot = project.version.toString().toDefaultLowerCase().contains("snapshot")

apply(plugin = "java")
apply(plugin = "signing")
apply(plugin = "idea")
apply(plugin = "com.github.ben-manes.versions")
apply(plugin = "com.adarshr.test-logger")
apply(plugin = "com.github.spotbugs")
apply(plugin = "com.dua3.cabe")
apply(plugin = "de.thetaphi.forbiddenapis")

jdk {
    version = 25
    javaFxBundled = true
    nativeImageCapable = true
}

java {
    withSourcesJar()
}

cabe {
    if (isReleaseVersion) {
        config.set(Configuration.parse("publicApi=THROW_IAE:privateApi=ASSERT"))
    } else {
        config.set(Configuration.DEVELOPMENT)
    }
}

dependencies {
    // JSpecify (source annotations)
    implementation(rootProject.libs.jspecify)

    // AtlantaFX
    implementation(rootProject.libs.atlantafx)

    // LOG4J
    implementation(platform(rootProject.libs.log4j.bom))
    implementation(rootProject.libs.log4j.api)

    // dua3 utility
    implementation(platform(rootProject.libs.dua3.utility.bom))
    implementation(rootProject.libs.dua3.utility)
    implementation(rootProject.libs.dua3.utility.logging)
    implementation(rootProject.libs.dua3.utility.fx)

    // JUnit
    testImplementation(platform(rootProject.libs.junit.bom))
    testImplementation(rootProject.libs.junit.jupiter.api)
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)
    testRuntimeOnly(rootProject.libs.junit.jupiter.engine)
}

idea {
    module {
        inheritOutputDirs = false
        outputDir = project.layout.buildDirectory.file("classes/java/main/").get().asFile
        testOutputDir = project.layout.buildDirectory.file("classes/java/test/").get().asFile
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()

            targets {
                all {
                    testTask {
                        // enable assertions and use headless mode for AWT in unit tests
                        jvmArgs("-ea", "-Djava.awt.headless=true")
                    }
                }
            }
        }
    }
}

testlogger {
    theme = ThemeType.MOCHA_PARALLEL
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:-module"))
    options.javaModuleVersion.set(provider { project.version as String })
    options.release.set(java.targetCompatibility.majorVersion.toInt())
}
tasks.compileTestJava {
    options.encoding = "UTF-8"
}
tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addStringOption("Xdoclint:all,-missing/private")
    }
}

// === verify JavaFX is available
tasks.register("verifyJavaFxSetup") {
    group = "verification"
    description = "Verifies that JavaFX is set up correctly."

    doLast {
        try {
            Class.forName("javafx.application.Application")
            println("JavaFX is correctly set up.")
        } catch (e: ClassNotFoundException) {
            throw GradleException("JavaFX is not set up correctly. Please ensure JavaFX is installed and available in your runtime environment.")
        }
    }
}

tasks.withType<JavaExec> {
    dependsOn("verifyJavaFxSetup")
    outputs.upToDateWhen { false }
}

// === TESTDATA DOWNLOAD ===
val testDataFiles = listOf(
    mapOf(
        "url" to "https://github.com/progit/progit2/releases/download/2.1.412/progit.pdf",
        "file" to "${rootDir}/testData/A/progit-2.1.412.pdf"
    ),
    mapOf(
        "url" to "https://github.com/progit/progit2/releases/download/2.1.438/progit.pdf",
        "file" to "${rootDir}/testData/B/progit-2.1.438.pdf"
    ),
    mapOf(
        "url" to "https://math.hws.edu/eck/cs424/downloads/graphicsbook-1.0.1.pdf",
        "file" to "${rootDir}/testData/A/graphicsbook-1.0.1.pdf"
    ),
    mapOf(
        "url" to "https://math.hws.edu/eck/cs424/downloads/graphicsbook-1.3.1.pdf",
        "file" to "${rootDir}/testData/B/graphicsbook-1.3.1.pdf"
    ),
    mapOf(
        "url" to "https://docs.oracle.com/javase/specs/jls/se17/jls17.pdf",
        "file" to "${rootDir}/testData/A/The Java Language Specification-Java SE 17 Edition.pdf"
    ),
    mapOf(
        "url" to "https://docs.oracle.com/javase/specs/jls/se21/jls21.pdf",
        "file" to "${rootDir}/testData/B/The Java Language Specification-Java SE 21 Edition.pdf"
    ),
)

// === FORBIDDEN APIS ===
tasks.withType(de.thetaphi.forbiddenapis.gradle.CheckForbiddenApis::class).configureEach {
    enabled = false // XXX plugin does not yet support Java 25
    bundledSignatures = setOf("jdk-internal", "jdk-deprecated")
    ignoreFailures = false
}

// === SPOTBUGS ===
spotbugs.toolVersion.set(rootProject.libs.versions.spotbugs)
spotbugs.excludeFilter.set(rootProject.file("spotbugs-exclude.xml"))

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    // SpotBugs is disabled for now (Java 25 / plugin support)
    enabled = false
}

/////////////////////////////////////////////////////////////////////////////
// Add run tasks for each locale
/////////////////////////////////////////////////////////////////////////////
val resourceDir = file("src/main/resources/dua3")
val localeFiles = resourceDir.listFiles { _, name ->
    name.startsWith("keystoremanager_") && name.endsWith(".properties")
} ?: emptyArray()

localeFiles.forEach { file ->
    val localeString = file.name.removePrefix("keystoremanager_").removeSuffix(".properties")
    val taskName = "run_$localeString"
    tasks.register<JavaExec>(taskName) {
        group = "application"
        description = "Run the application with locale $localeString"
        mainClass.set(application.mainClass)
        classpath = sourceSets.main.get().runtimeClasspath
        jvmArgs = application.applicationDefaultJvmArgs + listOf("-Duser.language=$localeString")
        // If locale contains country code (e.g. zh_Hant)
        if (localeString.contains('_')) {
            val parts = localeString.split('_')
            jvmArgs = application.applicationDefaultJvmArgs + listOf(
                "-Duser.language=${parts[0]}",
                "-Duser.country=${parts[1]}"
            )
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// Versions plugin configuration for all projects
/////////////////////////////////////////////////////////////////////////////

fun isStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "[0-9,.v-]+-(rc|ea|alpha|beta|b|M|SNAPSHOT)([+-]?[0-9]*)?".toRegex()
    return stableKeyword || !regex.matches(version)
}

tasks.withType<DependencyUpdatesTask> {
    // refuse non-stable versions
    rejectVersionIf {
        !isStable(candidate.version)
    }

    // dependencyUpdates fails in parallel mode with Gradle 9+ (https://github.com/ben-manes/gradle-versions-plugin/issues/968)
    doFirst {
        gradle.startParameter.isParallelProjectExecutionEnabled = false
    }
}

// === on macOS, wrap the native image as an application bundle
tasks.register("createMacApp") {
    group = "distribution"
    description = "Creates a macOS .app bundle for the native executable."

    // Only run this task if the operating system is macOS
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }

    // Ensure the native binary exists before wrapping it
    dependsOn("nativeCompile")

    doLast {
        val appName = "KeystoreManager"
        val version = project.version.toString()
        val buildDir = layout.buildDirectory.get().asFile

        val appBundle = file("$buildDir/distributions/$appName.app")
        val contentsDir = file("$appBundle/Contents")
        val macOSDir = file("$contentsDir/MacOS")
        val resourcesDir = file("$contentsDir/Resources")

        // 1. Clean and Create Directory Structure
        appBundle.deleteRecursively()
        macOSDir.mkdirs()
        resourcesDir.mkdirs()

        // 2. Copy the Native Binary
        val nativeBinary = file("$buildDir/native/nativeCompile/keystoremanager")
        if (!nativeBinary.exists()) {
            throw GradleException("Native binary not found at ${nativeBinary.path}. Run nativeCompile first.")
        }

        val targetBinary = file("$macOSDir/$appName")
        nativeBinary.copyTo(targetBinary, overwrite = true)
        targetBinary.setExecutable(true)

        // 3. Create the Info.plist
        // LSUIElement = false ensures it shows in the Dock
        // NSHighResolutionCapable = true ensures Retina support
        val plistContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>CFBundleExecutable</key>
                <string>$appName</string>
                <key>CFBundleIconFile</key>
                <string>logo.icns</string>
                <key>CFBundleIdentifier</key>
                <string>com.dua3.app.keystoremanager</string>
                <key>CFBundleName</key>
                <string>$appName</string>
                <key>CFBundlePackageType</key>
                <string>APPL</string>
                <key>CFBundleShortVersionString</key>
                <string>$version</string>
                <key>LSMinimumSystemVersion</key>
                <string>11.0</string>
                <key>NSHighResolutionCapable</key>
                <true/>
            </dict>
            </plist>
        """.trimIndent()

        file("$contentsDir/Info.plist").writeText(plistContent)

        // 4. Copy Icon
        val iconFile = file("data/logo.icns")
        if (iconFile.exists()) {
            iconFile.copyTo(file("$resourcesDir/logo.icns"), overwrite = true)
        }

        println("✅ macOS App Bundle created at: ${appBundle.absolutePath}")
    }
}