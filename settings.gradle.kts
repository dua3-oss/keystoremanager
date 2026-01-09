import org.gradle.internal.extensions.stdlib.toDefaultLowerCase

rootProject.name = "keystoremanager"

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
// Copyright (c) 2025 Axel Howind (axel@dua3.com)

val projectVersion = "0.1.0-SNAPSHOT"

dependencyResolutionManagement {

    val isReleaseCandidate = projectVersion.toDefaultLowerCase().contains("-rc")

    versionCatalogs {
        create("libs") {
            version("projectVersion", projectVersion)

            plugin("jdk", "com.dua3.gradle.jdkprovider").version("0.4.0")
            plugin("graalvm", "org.graalvm.buildtools.native").version("0.11.3")
            plugin("jlink", "org.beryx.jlink").version("3.2.0")
            plugin("versions", "com.github.ben-manes.versions").version("0.53.0")
            plugin("test-logger", "com.adarshr.test-logger").version("4.0.0")
            plugin("spotbugs", "com.github.spotbugs").version("6.4.8")
            plugin("cabe", "com.dua3.cabe").version("3.3.0")
            plugin("forbiddenapis", "de.thetaphi.forbiddenapis").version("3.10")

            version("bouncycastle", "1.83")
            version("dua3-utility", "20.4.3-SNAPSHOT")
            version("ikonli", "12.4.0")
            version("jspecify", "1.0.0")
            version("junit-bom", "6.0.1")
            version("log4j-bom", "2.25.3")
            version("atlantafx", "2.1.0")
            version("spotbugs", "4.9.8")

            library("atlantafx", "io.github.mkpaz", "atlantafx-base").versionRef("atlantafx")

            library("bouncycastle-provider", "org.bouncycastle", "bcprov-jdk18on").versionRef("bouncycastle")
            library("bouncycastle-pkix", "org.bouncycastle", "bcpkix-jdk18on").versionRef("bouncycastle")
            library("dua3-utility-bom", "com.dua3.utility", "utility-bom").versionRef("dua3-utility")
            library("dua3-utility", "com.dua3.utility", "utility").withoutVersion()
            library("dua3-utility-db", "com.dua3.utility", "utility-db").withoutVersion()
            library("dua3-utility-logging", "com.dua3.utility", "utility-logging").withoutVersion()
            library("dua3-utility-logging-log4j", "com.dua3.utility", "utility-logging-log4j").withoutVersion()
            library("dua3-utility-swing", "com.dua3.utility", "utility-swing").withoutVersion()
            library("dua3-utility-fx", "com.dua3.utility", "utility-fx").withoutVersion()
            library("dua3-utility-fx-controls", "com.dua3.utility", "utility-fx-controls").withoutVersion()
            library("dua3-utility-fx-db", "com.dua3.utility", "utility-fx-db").withoutVersion()
            library("dua3-utility-fx-icons", "com.dua3.utility", "utility-fx-icons").withoutVersion()
            library("dua3-utility-fx-icons-ikonli", "com.dua3.utility", "utility-fx-icons-ikonli").withoutVersion()
            library("dua3-utility-fx-web", "com.dua3.utility", "utility-fx-web").withoutVersion()
            library("ikonli-fontawesome", "org.kordamp.ikonli", "ikonli-fontawesome-pack").versionRef("ikonli")
            library("ikonli-feather", "org.kordamp.ikonli", "ikonli-feather-pack").versionRef("ikonli")
            library("ikonli-javafx", "org.kordamp.ikonli", "ikonli-javafx").versionRef("ikonli")
            library("jspecify", "org.jspecify", "jspecify").versionRef("jspecify")
            library("log4j-bom", "org.apache.logging.log4j", "log4j-bom").versionRef("log4j-bom")
            library("log4j-api", "org.apache.logging.log4j", "log4j-api").withoutVersion()
            library("junit-bom", "org.junit", "junit-bom").versionRef("junit-bom")
            library("junit-platform-launcher", "org.junit.platform", "junit-platform-launcher").withoutVersion()
            library("junit-jupiter-api", "org.junit.jupiter", "junit-jupiter-api").withoutVersion()
            library("junit-jupiter-engine", "org.junit.jupiter", "junit-jupiter-engine").withoutVersion()
        }
    }

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {

        // Maven Central Repository
        mavenLocal()
        mavenCentral()

        if (isReleaseCandidate) {
            println("release candidate version detected, adding Maven staging repositories")

            // Apache staging
            maven {
                name = "apache-staging"
                url = java.net.URI("https://repository.apache.org/content/repositories/staging/")
                mavenContent {
                    releasesOnly()
                }
            }
        }
    }

}
