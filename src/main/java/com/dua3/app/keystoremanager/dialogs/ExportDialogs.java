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
import com.dua3.utility.fx.controls.Dialogs;
import com.dua3.utility.fx.controls.InputControl;
import com.dua3.utility.fx.controls.InputControlState;
import com.dua3.utility.fx.controls.WizardDialogBuilder;
import com.dua3.utility.lang.LangUtil;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ExportDialogs {
    private static final Logger LOG = LogManager.getLogger(ExportDialogs.class);
    private static final String ID_KEYSTORE_FOLDER = "KEYSTORE_FOLDER";
    private static final String ID_KEYSTORE_NAME = "KEYSTORE_NAME";
    private static final String ID_KEYSTORE_TYPE = "KEYSTORE_TYPE";
    private static final String ID_KEYSTORE_PASSWORD = "KEYSTORE_PASSWORD";
    private static final String ID_EXPORT_MODE = "EXPORT_MODE";
    private static final String SELECTED_ALIASES = "SELECTED_ALIASES";
    private static final String ITEMS_TO_EXPORT = "Select Items to export";
    private static final String KEYSTORE_SETTINGS = "Select target kKeystore location";

    public enum ExportMode {
        FILE("Export to File"),
        CLIPBOARD("Copy BASE64 to Clipboard");

        private final String label;
        ExportMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record ExportSettings(ExportMode mode, Path path, KeyStoreType type, String password, Map<String, KeyStoreExportSelctionInput.ExportChoice> selection) {}

    private ExportDialogs() {}
    
    /**
     * A custom input control that provides a list of radio buttons for selecting an option.
     *
     * @param <T> the type of the options
     */
    private static class RadioList<T> implements InputControl<T> {
        private final VBox vbox;
        private final ObjectProperty<T> value = new SimpleObjectProperty<>();
        private final InputControlState<T> state;

        public RadioList(List<T> options, T defaultValue) {
            vbox = new VBox(5);
            ToggleGroup group = new ToggleGroup();
            for (T option : options) {
                RadioButton rb = new RadioButton(option.toString());
                rb.setToggleGroup(group);
                rb.setUserData(option);
                if (option.equals(defaultValue)) {
                    rb.setSelected(true);
                    value.set(defaultValue);
                }
                vbox.getChildren().add(rb);
            }
            group.selectedToggleProperty().addListener((obs, old, nw) -> {
                if (nw != null) {
                    value.set((T) nw.getUserData());
                }
            });
            state = new InputControlState<>(value, () -> defaultValue);
        }

        @Override public Node node() { return vbox; }
        @Override public InputControlState<T> state() { return state; }
    }

    /**
     * A custom input control that provides vertical space.
     */
    private static class SpaceControl implements InputControl<Void> {
        private final Region region = new Region();
        private final InputControlState<Void> state = new InputControlState<>(new SimpleObjectProperty<>(), () -> null);
        public SpaceControl(double height) { region.setPrefHeight(height); }
        @Override public Node node() { return region; }
        @Override public InputControlState<Void> state() { return state; }
    }

    public static Optional<ExportSettings> showExportDialog(
            Window owner,
            KeyStoreData keystore
    ) throws GeneralSecurityException {
        LOG.debug("Showing new export dialog.");

        // Create the dialog
        WizardDialogBuilder builder = Dialogs.wizard(owner)
                .title("Export to new Keystore");

        // Add a table showing the entries to let the user select what to export
        KeyStoreExportSelctionInput keyStoreExportSelectionInput = new KeyStoreExportSelctionInput(keystore);

        builder.page(ITEMS_TO_EXPORT, Dialogs.inputDialogPane()
                .header("WARNING: Do not export sensitive keys or certificates unless you know exactly what you are doing!")
                .addInput(SELECTED_ALIASES, Map.class, FXCollections::observableHashMap, keyStoreExportSelectionInput)
        );

        // where to write the keystore to
        RadioList<ExportMode> modeInput = new RadioList<>(List.of(ExportMode.values()).reversed(), ExportMode.FILE);
        builder.page(KEYSTORE_SETTINGS, Dialogs.inputDialogPane()
                .header("Select where to store the new keystore and provide a password for it.")
                .inputComboBox(ID_KEYSTORE_TYPE, "Type", () -> KeyStoreType.PKCS12, KeyStoreType.class, List.of(KeyStoreType.values()))
                .inputPasswordWithVerification(ID_KEYSTORE_PASSWORD, "Password", "Repeat Password")
                .addInput("space", Void.class, () -> null, new SpaceControl(20))
                .addInput(ID_EXPORT_MODE, ExportMode.class, () -> ExportMode.FILE, modeInput)
                .inputFolder(ID_KEYSTORE_FOLDER, "Target Folder", keystore.path()::getParent, true,
                        v -> modeInput.value.get() == ExportMode.FILE && v == null
                                ? Optional.of("Target folder is required.")
                                : Optional.empty())
                .inputString(ID_KEYSTORE_NAME, "Keystore name", () -> null,
                        v -> modeInput.value.get() == ExportMode.FILE && (v == null || v.isBlank())
                                ? Optional.of("Keystore name is required.")
                                : Optional.empty())
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
                    Map<String, KeyStoreExportSelctionInput.ExportChoice> selectedAliases =
                            (Map<String, KeyStoreExportSelctionInput.ExportChoice>) LangUtil.getOrThrow(
                                    (Map<String, Object>) LangUtil.getOrThrow(result, ITEMS_TO_EXPORT),
                                    SELECTED_ALIASES
                            );

                    return new ExportSettings(mode, path, type, password, selectedAliases);
                });
    }

}