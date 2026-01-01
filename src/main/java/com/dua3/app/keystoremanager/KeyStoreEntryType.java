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

import com.dua3.utility.i18n.I18N;

/**
 * Enum representing the type of entry in a KeyStore.
 * Each entry type is associated with a descriptive text label.
 */
public enum KeyStoreEntryType {
    /**
     * Represents an unknown type of entry in a KeyStore.
     */
    UNKNOWN("dua3.keystoremanager.type.unknown"),
    /**
     * Represents an entry type in the KeyStore for private keys.
     */
    PRIVATE_KEY("dua3.keystoremanager.type.private_key"),
    /**
     * Represents an entry type in a KeyStore that corresponds to a secret key.
     */
    SECRET_KEY("dua3.keystoremanager.type.secret_key"),
    /**
     * Represents a general key entry type in a KeyStore.
     */
    KEY("dua3.keystoremanager.type.key"),
    /**
     * Enum constant representing a certificate entry in a KeyStore.
     */
    CERTIFICTE("dua3.keystoremanager.type.certificate");

    private final String key;

    KeyStoreEntryType(String key) {
        this.key = key;
    }

    @Override
    public String toString() {
        return I18N.getInstance().get(key);
    }
}
