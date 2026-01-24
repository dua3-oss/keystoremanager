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

dependencyResolutionManagement {

    val isReleaseCandidate = settings.providers.gradleProperty("projectVersion").getOrElse("0.1.0-SNAPSHOT").toDefaultLowerCase().contains("-rc")

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
