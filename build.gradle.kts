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
import javax.inject.Inject
import org.gradle.process.ExecOperations
import org.gradle.api.file.FileSystemOperations
import java.util.Base64
import java.util.Locale
import java.io.ByteArrayOutputStream
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

plugins {
    id("java")
    id("signing")
    id("idea")
    id("application")
    alias(libs.plugins.jdk)
    alias(libs.plugins.graalvm)
    alias(libs.plugins.jlink)
    alias(libs.plugins.jreleaser)
    alias(libs.plugins.test.logger)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.cabe)
    alias(libs.plugins.forbiddenapis)
}

val resolvedProjectVersion = rootProject.libs.versions.projectVersion.get()
project.version = resolvedProjectVersion
val packagingVersion = resolvedProjectVersion
    .replace(Regex("[-.]\\w*(SNAPSHOT|ALPHA|BETA|RC).*", RegexOption.IGNORE_CASE), "")
    .ifBlank { "0.0.0" }

/**
 * Loads uncommitted release credentials for local builds. Environment variables
 * take precedence, so the same names can be supplied as GitHub Actions secrets.
 */
fun loadSecrets(file: File): Map<String, String> = buildMap {
    if (!file.isFile) return@buildMap
    file.forEachLine { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
        val separator = trimmed.indexOf('=')
        if (separator <= 0) return@forEachLine
        val key = trimmed.substring(0, separator).trim()
        val value = trimmed.substring(separator + 1).trim().removeSurrounding("\"").removeSurrounding("'")
        if (key.isNotEmpty()) put(key, value)
    }
}

val localSecrets = loadSecrets(rootProject.file(".secrets.env"))
fun releaseSecret(name: String): String? = (providers.environmentVariable(name).orNull ?: localSecrets[name])
    ?.trim()
    ?.removeSurrounding("\"")
    ?.removeSurrounding("'")

val macSigningIdentity = providers.gradleProperty("mac.identity").orNull ?: releaseSecret("MAC_DEV_SIGN_IDENTITY")
val macSigningCertificate = releaseSecret("MAC_DEV_SIGN_CERT_P12")
val macSigningCertificatePassword = releaseSecret("MAC_DEV_SIGN_CERT_PASSWORD")
val externalMacSigningKeychain = providers.gradleProperty("mac.keychain").orNull ?: releaseSecret("MAC_SIGN_KEYCHAIN")
val localMacSigningKeychain = layout.buildDirectory.file("macos-signing.keychain-db").get().asFile.absolutePath
val macSigningKeychain = externalMacSigningKeychain
    ?: localMacSigningKeychain.takeIf { !macSigningCertificate.isNullOrBlank() && !macSigningCertificatePassword.isNullOrBlank() }

// The Microsoft Store assigns the package identity when its name is reserved in
// Partner Center. MSIX packages uploaded there do not need a signing certificate:
// the Store validates and signs them during ingestion.
val msStoreIdentityName = releaseSecret("MS_STORE_IDENTITY_NAME")
val msStorePublisher = releaseSecret("MS_STORE_PUBLISHER")
val msStorePublisherDisplayName = releaseSecret("MS_STORE_PUBLISHER_DISPLAY_NAME")
    ?.takeIf { it.isNotBlank() }
    ?: "dua3"

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

// Keep JReleaser anchored at the repository root when its release tasks are
// invoked from CI or a local checkout. The token is intentionally supplied at
// execution time, never from the repository or .secrets.env.
jreleaser {
    gitRootSearch.set(true)
    release {
        github {
            repoOwner.set("dua3-oss")
            name.set("keystoremanager")
            token.set(providers.environmentVariable("JRELEASER_GITHUB_TOKEN").orElse(providers.environmentVariable("GITHUB_TOKEN")))
        }
    }
}

dependencyLocking {
    lockAllConfigurations()
}

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
                "-Os",
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
    includeLocales.set(listOf("en", "de", "es", "fr", "id", "it", "ja", "ko", "zh", "zh-Hant"))

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

        // jpackage requires a numeric version.
        appVersion = packagingVersion

        // For the DMG task to use the same version
        project.extra["jpackageVersion"] = appVersion
        
        // Always produce an app image; installers are optional depending on OS/flags
        imageName = "KeystoreManager"

        // Common runtime options
        jvmArgs = listOf("-Dprism.allowhidpi=true", "--enable-native-access=javafx.graphics,ALL-UNNAMED")

        // Use platform-appropriate icon from the data/ folder at project root
        val os = org.gradle.internal.os.OperatingSystem.current()
        // Users can pass platform-specific options on the command line
        if (os.isLinux) {
            installerOptions.addAll("--linux-app-release", "")
        }
        if (os.isMacOsX) {
            installerType = "dmg"
        }
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

        // macOS signing credentials may come from .secrets.env for a local
        // release or from same-named GitHub Actions organization secrets.
        if (os.isMacOsX && !macSigningIdentity.isNullOrBlank()) {
            // Sign the app image as well as the enclosing DMG. Supplying this
            // only as an installer option can leave the .app with an ad-hoc
            // signature, which cannot be notarized.
            imageOptions.addAll("--mac-sign", "--mac-app-image-sign-identity", macSigningIdentity)
            installerOptions.addAll("--mac-sign", "--mac-app-image-sign-identity", macSigningIdentity)
            if (!macSigningKeychain.isNullOrBlank()) {
                imageOptions.addAll("--mac-signing-keychain", macSigningKeychain)
                installerOptions.addAll("--mac-signing-keychain", macSigningKeychain)
            }
        }

        // Windows signing
        val winSign = (project.findProperty("win.sign") as String?)?.toBoolean() == true
        if (winSign) {
            val ks = (project.findProperty("win.keystore") as String?)?.trim().orEmpty()
            val ksp = (project.findProperty("win.storepass") as String?)?.trim().orEmpty()
            val alias = (project.findProperty("win.alias") as String?)?.trim().orEmpty()
            if (ks.isNotEmpty() && ksp.isNotEmpty() && alias.isNotEmpty()) {
                installerOptions.addAll(
                        "--win-sign",
                        "--win-signing-key-store", ks,
                        "--win-signing-key-store-pass", ksp,
                        "--win-signing-key-store-type", "pkcs12",
                        "--win-signing-key-alias", alias
                )
                val signOpts = (project.findProperty("win.signingOptions") as String?)?.trim()
                if (!signOpts.isNullOrEmpty()) {
                    // pass additional options to the signtool invocation (e.g. timestamp server)
                    installerOptions.addAll("--win-signing-options", signOpts)
                }
            }
        }
    }
}

// Copy jpackaged installers to the distribution directory
val copyJpackageInstallers = tasks.register<Sync>("copyJpackageInstallers") {
    group = "distribution"
    description = "Copies jpackage installers to the distribution directory."
    from(layout.buildDirectory.dir("jpackage")) {
        include("*.dmg", "*.pkg", "*.exe", "*.msi", "*.deb", "*.rpm")
    }
    into(layout.buildDirectory.dir("distributions"))
}

abstract class PrepareMacSigningKeychainTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Internal
    abstract val signingIdentity: Property<String>

    @get:Internal
    abstract val certificateBase64: Property<String>

    @get:Internal
    abstract val certificatePassword: Property<String>

    @get:Internal
    abstract val keychainPath: Property<String>

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun prepare() {
        val keychain = File(keychainPath.get())
        val certificate = temporaryDir.resolve("mac-signing-certificate.p12")
        keychain.parentFile.mkdirs()

        try {
            certificate.writeBytes(Base64.getDecoder().decode(certificateBase64.get()))
        } catch (e: IllegalArgumentException) {
            throw GradleException("MAC_DEV_SIGN_CERT_P12 must be Base64-encoded PKCS#12 data.", e)
        }

        try {
            execOperations.exec {
                commandLine("security", "delete-keychain", keychain.absolutePath)
                isIgnoreExitValue = true
            }
            execOperations.exec {
                commandLine("security", "create-keychain", "-p", "local-build", keychain.absolutePath)
            }
            execOperations.exec {
                commandLine("security", "set-keychain-settings", keychain.absolutePath)
            }
            execOperations.exec {
                commandLine("security", "unlock-keychain", "-p", "local-build", keychain.absolutePath)
            }
            execOperations.exec {
                commandLine(
                    "security", "import", certificate.absolutePath,
                    "-k", keychain.absolutePath,
                    "-f", "pkcs12",
                    "-P", certificatePassword.get(),
                    "-A"
                )
            }
            execOperations.exec {
                commandLine(
                    "security", "set-key-partition-list",
                    "-S", "apple-tool:,apple:",
                    "-s", "-k", "local-build", keychain.absolutePath
                )
            }
            val identities = ByteArrayOutputStream()
            execOperations.exec {
                commandLine("security", "find-identity", "-v", "-p", "codesigning", keychain.absolutePath)
                standardOutput = identities
            }
            check(identities.toString().contains(signingIdentity.get())) {
                "The imported keychain does not contain MAC_DEV_SIGN_IDENTITY."
            }
        } finally {
            certificate.delete()
        }
    }
}

val prepareMacSigningKeychain = tasks.register<PrepareMacSigningKeychainTask>("prepareMacSigningKeychain") {
    group = "distribution"
    description = "Imports the local macOS signing certificate into an ephemeral keychain."
    signingIdentity.set(macSigningIdentity)
    certificateBase64.set(macSigningCertificate)
    certificatePassword.set(macSigningCertificatePassword)
    keychainPath.set(localMacSigningKeychain)

    onlyIf {
        org.gradle.internal.os.OperatingSystem.current().isMacOsX &&
            externalMacSigningKeychain.isNullOrBlank() &&
            !macSigningIdentity.isNullOrBlank() &&
            !macSigningCertificate.isNullOrBlank() &&
            !macSigningCertificatePassword.isNullOrBlank()
    }
}

abstract class RemoveMacSigningKeychainTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Internal
    abstract val keychainPath: Property<String>

    @TaskAction
    fun remove() {
        execOperations.exec {
            commandLine("security", "delete-keychain", keychainPath.get())
            isIgnoreExitValue = true
        }
    }
}

val removeMacSigningKeychain = tasks.register<RemoveMacSigningKeychainTask>("removeMacSigningKeychain") {
    group = "distribution"
    description = "Removes the build-local macOS signing keychain."
    keychainPath.set(localMacSigningKeychain)
    onlyIf {
        org.gradle.internal.os.OperatingSystem.current().isMacOsX && externalMacSigningKeychain.isNullOrBlank()
    }
}

val verifyMacSigningConfiguration = tasks.register("verifyMacSigningConfiguration") {
    group = "distribution"
    description = "Verifies that macOS signing credentials are available."
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }

    doLast {
        check(!macSigningIdentity.isNullOrBlank()) {
            "MAC_DEV_SIGN_IDENTITY is required to create signed macOS artifacts."
        }
        check(!externalMacSigningKeychain.isNullOrBlank() ||
            (!macSigningCertificate.isNullOrBlank() && !macSigningCertificatePassword.isNullOrBlank())) {
            "Provide MAC_SIGN_KEYCHAIN, or both MAC_DEV_SIGN_CERT_P12 and MAC_DEV_SIGN_CERT_PASSWORD."
        }
    }
}

prepareMacSigningKeychain.configure {
    mustRunAfter(verifyMacSigningConfiguration)
}

abstract class CleanupMacInstallersTask @Inject constructor(
    private val fs: FileSystemOperations
) : DefaultTask() {
    @get:InputDirectory
    abstract val distributionsDir: DirectoryProperty

    @TaskAction
    fun cleanup() {
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            fs.delete {
                delete(distributionsDir.asFileTree.matching {
                    include("*.pkg")
                })
            }
        }
    }
}

val cleanupMacInstallers = tasks.register<CleanupMacInstallersTask>("cleanupMacInstallers") {
    group = "distribution"
    description = "Removes macOS PKG installers from the distribution directory."
    distributionsDir.set(layout.buildDirectory.dir("distributions"))
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    mustRunAfter("distZip", "distTar")
}

tasks.named("jpackage") {
    dependsOn(prepareMacSigningKeychain)
    finalizedBy(copyJpackageInstallers, removeMacSigningKeychain)
}

tasks.named("copyJpackageInstallers") {
    finalizedBy(cleanupMacInstallers)
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
val isWindowsArm = org.gradle.internal.os.OperatingSystem.current().isWindows &&
    System.getProperty("os.arch").toDefaultLowerCase() in setOf("aarch64", "arm64")

jdk {
    version = rootProject.libs.versions.jdk.get()
    javaFxBundled = true
    // The Native Image Kit is not available for Windows ARM.
    nativeImageCapable = !isWindowsArm
}

/**
 * Packages the Windows jpackage app image as an unsigned MSIX for Microsoft
 * Store ingestion. It is deliberately not suitable for sideloading: sideloaded
 * MSIX packages must be signed with a trusted certificate.
 */
abstract class CreateMsixTask @Inject constructor(
    private val execOperations: ExecOperations,
    private val fs: FileSystemOperations
) : DefaultTask() {
    @get:InputDirectory
    abstract val appImage: DirectoryProperty

    @get:InputFile
    abstract val icon: RegularFileProperty

    /** Directory containing the application's ResourceBundle properties files. */
    @get:InputDirectory
    abstract val resourceBundleDirectory: DirectoryProperty

    /** The ResourceBundle base name without its optional locale suffix. */
    @get:Input
    abstract val resourceBundleBaseName: Property<String>

    /** Language represented by the ResourceBundle with no locale suffix. */
    @get:Input
    abstract val defaultResourceLanguage: Property<String>

    @get:Input
    abstract val identityName: Property<String>

    @get:Input
    abstract val publisher: Property<String>

    @get:Input
    abstract val publisherDisplayName: Property<String>

    @get:Input
    abstract val packageVersion: Property<String>

    @get:Input
    abstract val architecture: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /**
     * The MSIX schema accepts a restricted X.500 distinguished name for the
     * Publisher attribute. Keep this validation local so a malformed secret
     * produces an actionable Gradle error without writing its value to logs.
     */
    private val publisherAttribute = """(?:CN|L|O|OU|E|C|S|STREET|T|G|I|SN|DC|SERIALNUMBER|Description|PostalCode|POBox|Phone|X21Address|dnQualifier|OID\.(?:0|[1-9]\d*)(?:\.(?:0|[1-9]\d*))+)"""
    private val publisherValue = """(?:[^,+="<>#;]+|".*")"""
    private val publisherPattern = Regex("""^$publisherAttribute=$publisherValue(?:, $publisherAttribute=$publisherValue)*$""")

    private fun xml(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun createLogo(source: File, target: File, size: Int) {
        val image = ImageIO.read(source) ?: throw GradleException("Unable to read MSIX icon: ${source.absolutePath}")
        val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = scaled.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(image, 0, 0, size, size, null)
        } finally {
            graphics.dispose()
        }
        ImageIO.write(scaled, "png", target)
    }

    private fun createWideLogo(source: File, target: File) {
        val image = ImageIO.read(source) ?: throw GradleException("Unable to read MSIX icon: ${source.absolutePath}")
        val wideLogo = BufferedImage(310, 150, BufferedImage.TYPE_INT_ARGB)
        val graphics = wideLogo.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(image, 80, 0, 150, 150, null)
        } finally {
            graphics.dispose()
        }
        ImageIO.write(wideLogo, "png", target)
    }

    /**
     * Maps Java ResourceBundle suffixes (for example, zh_Hant) to the BCP-47
     * language tags required by the MSIX manifest (zh-Hant).
     */
    private fun bundleLanguages(): List<String> {
        val baseName = resourceBundleBaseName.get()
        val filePattern = Regex("^${Regex.escape(baseName)}(?:_(.+))?\\.properties$")
        val defaultLanguage = defaultResourceLanguage.get().trim()
        check(defaultLanguage.matches(Regex("^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$"))) {
            "defaultResourceLanguage must be a BCP-47 language tag: $defaultLanguage"
        }

        return resourceBundleDirectory.get().asFile.listFiles().orEmpty()
            .filter { it.isFile }
            .mapNotNull { file ->
                filePattern.matchEntire(file.name)?.groupValues?.get(1)?.let { suffix ->
                    if (suffix.isEmpty()) {
                        defaultLanguage
                    } else {
                        suffix.split('_').mapIndexed { index, part ->
                            check(part.matches(Regex("^[A-Za-z0-9]{2,8}$"))) {
                                "Invalid ResourceBundle locale suffix in ${file.name}"
                            }
                            when {
                                index == 0 -> part.lowercase(Locale.ROOT)
                                part.length == 4 && part.all(Char::isLetter) ->
                                    part.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
                                part.length == 2 && part.all(Char::isLetter) || part.length == 3 && part.all(Char::isDigit) ->
                                    part.uppercase(Locale.ROOT)
                                else -> part
                            }
                        }.joinToString("-")
                    }
                }
            }
            .distinct()
            .sorted()
            .also { check(it.isNotEmpty()) { "No $baseName*.properties ResourceBundle files found." } }
    }

    private fun makeAppx(): File {
        System.getenv("MAKEAPPX_PATH")?.trim()?.takeIf { it.isNotEmpty() }?.let { configured ->
            val executable = File(configured)
            check(executable.isFile) { "MAKEAPPX_PATH does not point to MakeAppx.exe: ${executable.absolutePath}" }
            return executable
        }

        System.getenv("PATH").orEmpty().split(File.pathSeparator).forEach { directory ->
            val executable = File(directory, "MakeAppx.exe")
            if (executable.isFile) return executable
        }

        val sdkRoot = File(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "Windows Kits\\10\\bin")
        if (sdkRoot.isDirectory) {
            val hostArchitecture = when (System.getProperty("os.arch").toDefaultLowerCase()) {
                "aarch64", "arm64" -> "arm64"
                else -> "x64"
            }
            val executables = sdkRoot.walkTopDown()
                .filter { it.isFile && it.name.equals("MakeAppx.exe", ignoreCase = true) }
                .toList()
            executables
                .filter { it.parentFile.name.equals(hostArchitecture, ignoreCase = true) }
                .maxByOrNull { it.parentFile.parentFile.name }
                ?.let { return it }
            executables.maxByOrNull { it.parentFile.parentFile.name }?.let { return it }
        }
        throw GradleException(
            "MakeAppx.exe was not found. Install the Windows 10/11 SDK, or set MAKEAPPX_PATH to its full path."
        )
    }

    @TaskAction
    fun create() {
        check(org.gradle.internal.os.OperatingSystem.current().isWindows) {
            "createMsix must run on Windows."
        }
        check(identityName.get().isNotBlank()) {
            "MS_STORE_IDENTITY_NAME is required. Copy the Package/Identity/Name value from Partner Center."
        }
        check(publisher.get().isNotBlank()) {
            "MS_STORE_PUBLISHER is required. Copy the Package/Identity/Publisher value from Partner Center."
        }
        check(publisherPattern.matches(publisher.get())) {
            "MS_STORE_PUBLISHER must be the exact X.500 Package/Identity/Publisher value from Partner Center " +
                "(normally CN=<publisher ID>), not the publisher display name. Do not include shell quotes or " +
                "the MS_STORE_PUBLISHER= assignment."
        }
        val staging = temporaryDir.resolve("msix")
        val assets = staging.resolve("Assets")
        fs.delete { delete(staging) }
        assets.mkdirs()

        fs.copy {
            from(appImage)
            into(staging.resolve("KeystoreManager"))
        }
        createLogo(icon.get().asFile, assets.resolve("Square44x44Logo.png"), 44)
        createLogo(icon.get().asFile, assets.resolve("Square150x150Logo.png"), 150)
        createLogo(icon.get().asFile, assets.resolve("StoreLogo.png"), 50)
        createWideLogo(icon.get().asFile, assets.resolve("Wide310x150Logo.png"))

        val resourceLanguages = bundleLanguages().joinToString("\n") { language ->
            "    <Resource Language=\"${xml(language)}\" />"
        }

        staging.resolve("AppxManifest.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
         xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"
         xmlns:rescap="http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"
         IgnorableNamespaces="uap rescap">
  <Identity Name="${xml(identityName.get())}" Publisher="${xml(publisher.get())}" Version="${xml(packageVersion.get())}" ProcessorArchitecture="${xml(architecture.get())}" />
  <Properties>
    <DisplayName>Keystore Manager</DisplayName>
    <PublisherDisplayName>${xml(publisherDisplayName.get())}</PublisherDisplayName>
    <Logo>Assets\StoreLogo.png</Logo>
  </Properties>
  <Resources>
$resourceLanguages
  </Resources>
  <Dependencies>
    <TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.17763.0" MaxVersionTested="10.0.26100.0" />
  </Dependencies>
  <Applications>
    <Application Id="App" Executable="KeystoreManager\KeystoreManager.exe" EntryPoint="Windows.FullTrustApplication">
      <uap:VisualElements DisplayName="Keystore Manager" Description="Manage cryptographic keystores" BackgroundColor="transparent" Square150x150Logo="Assets\Square150x150Logo.png" Square44x44Logo="Assets\Square44x44Logo.png">
        <uap:DefaultTile Wide310x150Logo="Assets\Wide310x150Logo.png" />
      </uap:VisualElements>
    </Application>
  </Applications>
  <Capabilities><rescap:Capability Name="runFullTrust" /></Capabilities>
</Package>
""".trimIndent() + "\n"
        )

        val destination = outputFile.get().asFile
        destination.parentFile.mkdirs()
        destination.delete()
        execOperations.exec {
            commandLine(makeAppx().absolutePath, "pack", "/d", staging.absolutePath, "/p", destination.absolutePath, "/o")
        }
    }
}

fun msixVersion(version: String): String {
    val components = version.split('.').map {
        it.toIntOrNull() ?: throw GradleException("MSIX version must be numeric: $version")
    }
    require(components.size <= 4 && components.all { it in 0..65535 }) {
        "MSIX version must contain at most four components from 0 through 65535: $version"
    }
    return (components + List(4 - components.size) { 0 }).joinToString(".")
}

val msixArchitecture = when (System.getProperty("os.arch").toDefaultLowerCase()) {
    "amd64", "x86_64" -> "x64"
    "aarch64", "arm64" -> "arm64"
    "x86", "i386" -> "x86"
    else -> throw GradleException("Unsupported MSIX architecture: ${System.getProperty("os.arch")}")
}

val createMsix = tasks.register<CreateMsixTask>("createMsix") {
    group = "distribution"
    description = "Creates an unsigned MSIX package for Microsoft Store ingestion."
    // The app image lives under jpackage's declared output directory.  Keep
    // this explicit dependency so Gradle can validate the producer/consumer
    // relationship (in particular with Gradle 9's strict task validation).
    dependsOn("jpackage")
    appImage.set(layout.buildDirectory.dir("jpackage/KeystoreManager"))
    icon.set(layout.projectDirectory.file("data/logo.png"))
    resourceBundleDirectory.set(layout.projectDirectory.dir("src/main/resources/dua3"))
    resourceBundleBaseName.set("keystoremanager")
    // The unsuffixed ResourceBundle is the application's English fallback.
    defaultResourceLanguage.set("en")
    identityName.set(msStoreIdentityName ?: "")
    publisher.set(msStorePublisher ?: "")
    publisherDisplayName.set(msStorePublisherDisplayName)
    packageVersion.set(msixVersion(packagingVersion))
    architecture.set(msixArchitecture)
    outputFile.set(layout.buildDirectory.file("distributions/KeystoreManager-${msixVersion(packagingVersion)}-$msixArchitecture.msix"))
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
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
    implementation(rootProject.libs.dua3.utility.fx)

    implementation(rootProject.libs.slb4j)

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
        val test = getByName<JvmTestSuite>("test") {
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
}

// === on macOS, wrap the native image as an application bundle
abstract class CreateMacAppTask : DefaultTask() {
    @get:Input
    abstract val appVersion: Property<String>

    @get:InputFile
    abstract val nativeBinary: RegularFileProperty

    @get:InputFile
    @get:org.gradle.api.tasks.Optional
    abstract val iconFile: RegularFileProperty

    @get:OutputDirectory
    abstract val appBundle: DirectoryProperty

    @TaskAction
    fun createMacApp() {
        val appName = "KeystoreManager"
        val version = appVersion.get()
        val appBundleDir = appBundle.get().asFile
        val contentsDir = File(appBundleDir, "Contents")
        val macOSDir = File(contentsDir, "MacOS")
        val resourcesDir = File(contentsDir, "Resources")

        // 1. Clean and Create Directory Structure
        appBundleDir.deleteRecursively()
        macOSDir.mkdirs()
        resourcesDir.mkdirs()

        // 2. Copy the Native Binary
        val binary = nativeBinary.get().asFile
        if (!binary.exists()) {
            throw GradleException("Native binary not found at ${binary.path}. Run nativeCompile first.")
        }

        val targetBinary = File(macOSDir, appName)
        binary.copyTo(targetBinary, overwrite = true)
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

        File(contentsDir, "Info.plist").writeText(plistContent)

        // 4. Copy Icon
        if (iconFile.isPresent && iconFile.get().asFile.exists()) {
            val icon = iconFile.get().asFile
            val destIcon = File(resourcesDir, "logo.icns")
            icon.copyTo(destIcon, overwrite = true)
        }

        println("✅ macOS App Bundle created at: ${appBundleDir.absolutePath}")
    }
}

tasks.register<CreateMacAppTask>("createMacApp") {
    group = "distribution"
    description = "Creates a macOS .app bundle for the native executable."

    // Only run this task if the operating system is macOS
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }

    // Ensure the native binary exists before wrapping it
    dependsOn("nativeCompile")

    appVersion.set(project.provider { packagingVersion })
    nativeBinary.set(layout.buildDirectory.file("native/nativeCompile/keystoremanager"))
    iconFile.set(project.layout.projectDirectory.file("data/logo.icns"))
    appBundle.set(layout.buildDirectory.dir("native/bundle/KeystoreManager.app"))
}

abstract class SignMacAppTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:InputDirectory
    abstract val appBundle: DirectoryProperty

    @get:Internal
    abstract val signingIdentity: Property<String>

    @get:Internal
    abstract val signingKeychain: Property<String>

    @TaskAction
    fun sign() {
        val bundle = appBundle.get().asFile
        val executable = File(bundle, "Contents/MacOS/KeystoreManager")
        check(executable.isFile) { "Missing native executable: ${executable.absolutePath}" }

        val keychainArguments = signingKeychain.orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { listOf("--keychain", it) }
            ?: emptyList()
        fun sign(target: File) {
            execOperations.exec {
                commandLine(
                    listOf("codesign", "--force", "--options", "runtime", "--timestamp", "--sign", signingIdentity.get()) +
                        keychainArguments + target.absolutePath
                )
            }
        }

        sign(executable)
        sign(bundle)
        execOperations.exec {
            commandLine("codesign", "--verify", "--deep", "--strict", "--verbose=2", bundle.absolutePath)
        }
    }
}

val signMacApp = tasks.register<SignMacAppTask>("signMacApp") {
    group = "distribution"
    description = "Signs the GraalVM native macOS application bundle."
    dependsOn("createMacApp", prepareMacSigningKeychain)
    appBundle.set(layout.buildDirectory.dir("native/bundle/KeystoreManager.app"))
    signingIdentity.set(macSigningIdentity)
    signingKeychain.set(macSigningKeychain)

    onlyIf {
        org.gradle.internal.os.OperatingSystem.current().isMacOsX && !macSigningIdentity.isNullOrBlank()
    }
}

signMacApp.configure {
    finalizedBy(removeMacSigningKeychain)
}

abstract class CreateDmgTask @Inject constructor(
    private val execOperations: ExecOperations,
    private val fs: FileSystemOperations,
    private val layout: ProjectLayout
) : DefaultTask() {
    @get:Input
    @get:org.gradle.api.tasks.Optional
    abstract val appVersion: Property<String>

    @get:Internal
    abstract val jpackageVersion: Property<String>

    @TaskAction
    fun createDmg() {
        val version = if (appVersion.isPresent) appVersion.get() else jpackageVersion.get()
        val appName = "KeystoreManager"
        val buildDir = layout.buildDirectory.get().asFile
        val distributionsDir = File(buildDir, "distributions")
        distributionsDir.mkdirs()
        val appBundle = File(buildDir, "native/bundle/$appName.app") // native .app from createMacApp
        val dmgName = "$appName-$version-native"
        val finalDmg = File(distributionsDir, "$dmgName.dmg")

        if (!appBundle.exists()) {
            throw GradleException("App bundle not found at ${appBundle.absolutePath}. Run createMacApp first.")
        }

        // 1. Find the template DMG created by jpackage
        // jpackage puts its output in build/jpackage
        val jpackageOutputDir = File(buildDir, "jpackage")
        val templateDmg = jpackageOutputDir.listFiles { _, name ->
            name.startsWith(appName.lowercase()) && name.endsWith(".dmg")
        }?.firstOrNull() ?: throw GradleException("jpackaged DMG not found in ${jpackageOutputDir.absolutePath}. Run jpackage first.")

        println("Using template DMG: ${templateDmg.absolutePath}")

        // 2. Create a writable temporary DMG from the template
        val tempDmg = File(buildDir, "temp-native.dmg")
        fs.delete { delete(tempDmg) }
        
        println("Creating writable temporary DMG...")
        execOperations.exec {
            commandLine("hdiutil", "convert", templateDmg.absolutePath, "-format", "UDRW", "-o", tempDmg.absolutePath)
        }

        // 3. Mount the temporary DMG
        println("Mounting DMG for bundle swap...")
        val mountDir = File(buildDir, "mnt-native")
        mountDir.mkdirs()
        execOperations.exec {
            commandLine("hdiutil", "attach", tempDmg.absolutePath, "-mountpoint", mountDir.absolutePath)
        }

        try {
            // 4. Replace the .app bundle
            // The template DMG should have the jlinked .app bundle at its root
            val jlinkedApp = File(mountDir, "$appName.app")
            if (jlinkedApp.exists()) {
                println("Removing jlinked app bundle from DMG...")
                fs.delete { delete(jlinkedApp) }
            } else {
                println("Warning: $appName.app not found in DMG root. Trying to find it...")
                mountDir.listFiles()?.forEach { println("Found in DMG: ${it.name}") }
            }

            println("Copying native app bundle into DMG...")
            fs.copy {
                from(appBundle)
                into(File(mountDir, "$appName.app"))
            }
            
        } finally {
            // 5. Unmount the DMG. On hosted macOS runners, hdiutil can retain
            // the HFS image briefly after the copy has completed. Retry a
            // normal detach before using the safe forced-detach fallback.
            println("Unmounting DMG...")
            var detached = false
            for (attempt in 1..3) {
                val result = execOperations.exec {
                    commandLine("hdiutil", "detach", mountDir.absolutePath)
                    isIgnoreExitValue = true
                }
                detached = result.exitValue == 0
                if (detached) {
                    break
                }
                if (attempt < 3) {
                    println("DMG is still busy; retrying detach (attempt ${attempt + 1} of 3)...")
                    Thread.sleep(2000)
                }
            }
            if (!detached) {
                println("Normal detach remained busy; forcing detach...")
                val result = execOperations.exec {
                    commandLine("hdiutil", "detach", mountDir.absolutePath, "-force")
                    isIgnoreExitValue = true
                }
                if (result.exitValue != 0) {
                    throw GradleException("Unable to detach mounted DMG at ${mountDir.absolutePath}.")
                }
            }
            fs.delete { delete(mountDir) }
        }

        // 6. Convert to final read-only DMG
        fs.delete { delete(finalDmg) }
        println("Converting to final DMG...")
        execOperations.exec {
            commandLine("hdiutil", "convert", tempDmg.absolutePath, "-format", "UDZO", "-o", finalDmg.absolutePath)
        }

        fs.delete { delete(tempDmg) }
        println("✅ Native DMG created at: ${finalDmg.absolutePath}")
    }
}

tasks.register<CreateDmgTask>("createNativeDmg") {
    group = "distribution"
    description = "Creates a macOS DMG for the GraalVM native executable by swapping the bundle in the jpackaged DMG."
    dependsOn(signMacApp)
    mustRunAfter("jpackage")
    mustRunAfter("copyJpackageInstallers")
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }

    appVersion.set(provider { packagingVersion })
    jpackageVersion.set(provider { packagingVersion })
}

tasks.register("createDistributions") {
    group = "distribution"
    description = "Creates all configured distributions (native installer, jpackaged apps, archives)."

    dependsOn("jpackage")
    dependsOn("createNativeDmg")
}

val assembleSignedArtifacts = tasks.register("assembleSignedArtifacts") {
    group = "distribution"
    description = "Creates all signed release artifacts for the current platform."

    dependsOn("jpackage")
    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
        dependsOn(verifyMacSigningConfiguration, "createNativeDmg")
    }
}

abstract class NotarizeMacDistributionsTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Internal
    abstract val keyId: Property<String>

    @get:Internal
    abstract val issuerId: Property<String>

    @get:Internal
    abstract val privateKeyBase64: Property<String>

    @get:InputDirectory
    abstract val distributionsDir: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun notarize() {
        check(keyId.isPresent) { "APPLE_NOTARY_KEY_ID is required for macOS notarization." }
        check(issuerId.isPresent) { "APPLE_NOTARY_ISSUER_ID is required for macOS notarization." }
        check(privateKeyBase64.isPresent) { "APPLE_NOTARY_KEY_P8 is required for macOS notarization." }

        val apiKey = temporaryDir.resolve("apple-notary-key.p8")
        try {
            apiKey.writeBytes(Base64.getDecoder().decode(privateKeyBase64.get()))
        } catch (e: IllegalArgumentException) {
            throw GradleException("APPLE_NOTARY_KEY_P8 must be Base64-encoded API key data.", e)
        }
        apiKey.setReadable(false, false)
        apiKey.setReadable(true, true)

        try {
            val dmgs = distributionsDir.get().asFile.listFiles { file ->
                file.isFile && file.extension.equals("dmg", ignoreCase = true)
            }?.sortedBy { it.name } ?: emptyList()
            check(dmgs.isNotEmpty()) { "No DMG files found in ${distributionsDir.get().asFile.absolutePath}." }

            dmgs.forEach { dmg ->
                execOperations.exec {
                    commandLine(
                        "xcrun", "notarytool", "submit", dmg.absolutePath,
                        "--key", apiKey.absolutePath,
                        "--key-id", keyId.get(),
                        "--issuer", issuerId.get(),
                        "--wait"
                    )
                }
                execOperations.exec {
                    commandLine("xcrun", "stapler", "staple", dmg.absolutePath)
                }
                execOperations.exec {
                    commandLine("xcrun", "stapler", "validate", dmg.absolutePath)
                }
            }
        } finally {
            apiKey.delete()
        }
    }
}

val notarizeMacDistributions = tasks.register<NotarizeMacDistributionsTask>("notarizeMacDistributions") {
    group = "distribution"
    description = "Notarizes and staples all macOS DMGs in the distributions directory."
    dependsOn(assembleSignedArtifacts)
    keyId.set(releaseSecret("APPLE_NOTARY_KEY_ID"))
    issuerId.set(releaseSecret("APPLE_NOTARY_ISSUER_ID"))
    privateKeyBase64.set(releaseSecret("APPLE_NOTARY_KEY_P8"))
    distributionsDir.set(layout.buildDirectory.dir("distributions"))
    onlyIf {
        org.gradle.internal.os.OperatingSystem.current().isMacOsX && !isSnapshot
    }
}

tasks.register("createSignedArtifacts") {
    group = "distribution"
    description = "Creates all signed release artifacts and notarizes macOS release DMGs."
    dependsOn(assembleSignedArtifacts, notarizeMacDistributions)
}
