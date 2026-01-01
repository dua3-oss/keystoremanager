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
package com.dua3.app.keystoremanager.dialogs;

import com.dua3.app.keystoremanager.KeyStoreData;
import com.dua3.utility.crypt.KeyStoreType;
import com.dua3.utility.i18n.I18N;
import com.dua3.utility.fx.controls.Dialogs;
import com.dua3.utility.fx.controls.WizardDialogBuilder;
import com.dua3.utility.lang.LangUtil;
import javafx.collections.FXCollections;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class ExportDialogs {
    private static final Logger LOG = LogManager.getLogger(ExportDialogs.class);
    private static final I18N I18N = com.dua3.utility.i18n.I18N.getInstance();
    private static final String ID_KEYSTORE_FOLDER = "KEYSTORE_FOLDER";
    private static final String ID_KEYSTORE_NAME = "KEYSTORE_NAME";
    private static final String ID_KEYSTORE_TYPE = "KEYSTORE_TYPE";
    private static final String ID_KEYSTORE_PASSWORD = "KEYSTORE_PASSWORD";
    private static final String ID_EXPORT_MODE = "EXPORT_MODE";
    private static final String SELECTED_ALIASES = "SELECTED_ALIASES";
    private static final String ITEMS_TO_EXPORT = I18N.get("dua3.keystoremanager.dialog.export.page.items_to_export");
    private static final String KEYSTORE_SETTINGS = I18N.get("dua3.keystoremanager.dialog.export.page.keystore_settings");

    public enum ExportMode {
        FILE(I18N.get("dua3.keystoremanager.dialog.export.mode.file")),
        CLIPBOARD(I18N.get("dua3.keystoremanager.dialog.export.mode.clipboard"));

        private final String label;
        ExportMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record ExportSettings(ExportMode mode, @Nullable Path path, KeyStoreType type, String password, Map<String, KeyStoreExportSelectionInput.ExportChoice> selection) {}

    private ExportDialogs() {}
    
    public static Optional<ExportSettings> showExportDialog(
            Window owner,
            KeyStoreData keystore
    ) throws GeneralSecurityException {
        LOG.debug("Showing new export dialog.");

        // Create the dialog
        WizardDialogBuilder builder = Dialogs.wizard(owner)
                .title(I18N.get("dua3.keystoremanager.dialog.export.title"));

        // Add a table showing the entries to let the user select what to export
        KeyStoreExportSelectionInput keyStoreExportSelectionInput = new KeyStoreExportSelectionInput(keystore);

        builder.page(ITEMS_TO_EXPORT, Dialogs.inputDialogPane()
                .header(I18N.get("dua3.keystoremanager.dialog.export.header.warning"))
                .addInput(SELECTED_ALIASES, Map.class, FXCollections::observableHashMap, keyStoreExportSelectionInput)
        );

        AtomicReference<@Nullable ExportMode> exportMode = new AtomicReference<>(null);
        AtomicReference<@Nullable Path> targetFolder = new AtomicReference<>(null);
        AtomicReference<@Nullable String> keystoreName = new AtomicReference<>(null);
        Function<@Nullable Object, Optional<String>> velidateExportSettings = ignored ->
                switch (exportMode.get()) {
                    case null -> Optional.of(I18N.get("dua3.keystoremanager.dialog.export.validator.select_mode"));
                    case CLIPBOARD -> Optional.empty();
                    case FILE -> {
                        if (targetFolder.get() == null) {
                            yield Optional.of(I18N.get("dua3.keystoremanager.dialog.export.validator.select_folder"));
                        }
                        if (keystoreName.get() == null) {
                            yield Optional.of(I18N.get("dua3.keystoremanager.dialog.export.validator.select_name"));
                        }
                        yield Optional.empty();
                    }
                };
        builder.page(KEYSTORE_SETTINGS, Dialogs.inputDialogPane()
                .header(I18N.get("dua3.keystoremanager.dialog.export.header.settings"))
                .inputComboBox(ID_KEYSTORE_TYPE, I18N.get("dua3.keystoremanager.dialog.export.type"), () -> KeyStoreType.PKCS12, KeyStoreType.class, List.of(KeyStoreType.values()))
                .inputPasswordWithVerification(ID_KEYSTORE_PASSWORD, I18N.get("dua3.keystoremanager.dialog.export.password"), I18N.get("dua3.keystoremanager.dialog.export.password.repeat"))
                .inputRadioList(
                        ID_EXPORT_MODE,
                        I18N.get("dua3.keystoremanager.dialog.export.mode"),
                        () -> ExportMode.CLIPBOARD,
                        ExportMode.class,
                        List.of(ExportMode.values()),
                        value -> {
                            exportMode.set(value);
                            Optional<String> result = velidateExportSettings.apply(value);
                            LOG.trace("Export Mode - validateExportSettings: {}", result);
                            return result;
                        }
                )
                .inputFolder(ID_KEYSTORE_FOLDER, I18N.get("dua3.keystoremanager.dialog.export.target_folder"), keystore.path()::getParent, true,
                        value -> {
                            targetFolder.set(value);
                            Optional<String> result = velidateExportSettings.apply(value);
                            LOG.trace("Target Folder - validateExportSettings: {}", result);
                            return result;
                        }
                )
                .inputString(ID_KEYSTORE_NAME, I18N.get("dua3.keystoremanager.dialog.export.keystore_name"), () -> null,
                        value -> {
                            keystoreName.set(value);
                            Optional<String> result = velidateExportSettings.apply(value);
                            LOG.trace("Keystore name - validateExportSettings: {}", result);
                            return result;
                        }
                )
        );

        // show the wizard
        return builder.showAndWait()
                .map(result -> {
                    // extract general keystore settings
                    Map<String, Object> settings = (Map<String, Object>) LangUtil.getOrThrow(result, KEYSTORE_SETTINGS);

                    ExportMode mode = (ExportMode) LangUtil.getOrThrow(settings, ID_EXPORT_MODE);
                    KeyStoreType type = (KeyStoreType) LangUtil.getOrThrow(settings, ID_KEYSTORE_TYPE);
                    String password = (String) LangUtil.getOrThrow(settings, ID_KEYSTORE_PASSWORD);
                    Path path = null;
                    if (mode == ExportMode.FILE) {
                        Path folder = (Path) LangUtil.getOrThrow(settings, ID_KEYSTORE_FOLDER);
                        String name = (String) LangUtil.getOrThrow(settings, ID_KEYSTORE_NAME);
                        path = folder.resolve(name + "." + type.getExtension());
                    }

                    // extract selected entries
                    Map<String, KeyStoreExportSelectionInput.ExportChoice> selectedAliases =
                            (Map<String, KeyStoreExportSelectionInput.ExportChoice>) LangUtil.getOrThrow(
                                    (Map<String, Object>) LangUtil.getOrThrow(result, ITEMS_TO_EXPORT),
                                    SELECTED_ALIASES
                            );

                    return new ExportSettings(mode, path, type, password, selectedAliases);
                });
    }

}