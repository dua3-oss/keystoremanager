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
import org.jspecify.annotations.NullMarked;

@NullMarked
open module keystoremanager {
    exports com.dua3.app.keystoremanager;

    requires atlantafx.base;
    requires com.dua3.utility;
    requires com.dua3.utility.fx;
    requires com.dua3.utility.fx.controls;
    requires com.dua3.utility.logging;
    requires com.dua3.utility.logging.log4j;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires java.desktop;
    requires java.logging;
    requires java.prefs;
    requires javafx.graphics;
    requires javafx.controls;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;
    requires org.jspecify;
    requires javafx.base;
    
    provides com.dua3.utility.i18n.I18NProvider with com.dua3.app.keystoremanager.ApplicationI18NProvider;
}