/*
 * This file is part of Keystore Manager.
 *
 * Keystore Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published
 * by the Free Software Foundation.
 *
 * Keystore Manager is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Keystore Manager. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2025 Axel Howind
 */
package com.dua3.app.keystoremanager;

import com.dua3.utility.fx.FxLauncher;
import com.dua3.utility.i18n.I18N;

/**
 * The Main class serves as the entry point of the application.
 * It invokes the {@code FxLauncher.launchApplication} method to initiate
 * the KeyStore Manager application.
 */
public final class Main {
    private Main() {}

    /**
     * The main method serves as the entry point of the application.
     * It launches the KeyStore Manager application using the FxLauncher framework.
     *
     * @param args command-line arguments passed to the application
     */
    public static void main(String[] args) {
        I18N i18n = I18N.getInstance();
        FxLauncher.launchApplication(
                "com.dua3.app.keystoremanager.KeyStoreManager",
                args,
                i18n.get("dua3.keystoremanager.main.title"),
                "0.0.1",
                i18n.get("dua3.keystoremanager.main.copyright"),
                "axh@dua3.com",
                i18n.get("dua3.keystoremanager.main.description")
        );
    }
}
