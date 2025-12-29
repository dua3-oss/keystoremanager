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
import com.dua3.app.keystoremanager.KeyStoreEntryType;
import com.dua3.utility.crypt.KeyStoreUtil;
import com.dua3.utility.fx.controls.InputControl;
import com.dua3.utility.fx.controls.InputControlState;
import javafx.beans.property.MapProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleMapProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ListChangeListener;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

public class KeyStoreExportSelctionInput implements InputControl {
    private static final Logger LOG = LogManager.getLogger(KeyStoreExportSelctionInput.class);
    
    /**
     * An enumeration representing the export options for a key or key pair.
     * <p>
     * Overrides the toString method to provide a user-friendly label for each
     * option.
     */
    public enum ExportChoice {
        /**
         * Represents the choice to disable export functionality for a key or key pair.
         * Indicates that no export action should be performed.
         */
        NONE("Do not Export"),
        /**
         * Specifies the option to export only the public key of a key pair.
         */
        PUBLIC_ONLY("Public Key"),
        /**
         * Represents the option to export both the public and private keys in the
         * key or key pair export process.
         */
        PUBLIC_AND_PRIVATE("Public and Private Keys"),
        /**
         * Represents an export option for keys or key pairs.
         * Specifically, this option corresponds to exporting the key material.
         */
        EXPORT("Export");

        /**
         * The value to return in toString().
         */
        private final String label;

        ExportChoice(String label) { this.label = label; }

        /**
         * Override toString() to return a user-friendly text representation.
         *
         * @return the user-friendly label of the enumeration constant
         */
        @Override public String toString() { return label; }
    }

    private record Row(String alias, KeyStoreEntryType type, ObjectProperty<ExportChoice> choice) {}

    private MapProperty<String, ExportChoice> value = new SimpleMapProperty<>();
    private final InputControlState<ObservableMap<String, ExportChoice>> state;
    private final TableView<Row> table;
    // listeners to keep value map in sync with row choices
    private final Map<Row, ChangeListener<ExportChoice>> rowChoiceListeners = new HashMap<>();

    public KeyStoreExportSelctionInput(KeyStoreData keystore) throws GeneralSecurityException {
        this.state = new InputControlState<>(value, FXCollections::observableHashMap);
        if (this.value.get() == null) {
            this.value.set(FXCollections.observableHashMap());
        }
        this.table = createTable(keystore);
        bindValueToTable(this.table);
    }

    @Override
    public InputControlState state() {
        return state;
    }

    @Override
    public Node node() {
        return table;
    }

    private static TableView<Row> createTable(KeyStoreData keystore) throws GeneralSecurityException {
        TableView<Row> table = new TableView<>();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        TableColumn<Row, String> colAlias = new TableColumn<>("Item Name");
        colAlias.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().alias()));
        colAlias.setPrefWidth(200);

        TableColumn<Row, KeyStoreEntryType> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().type()));
        colType.setPrefWidth(140);

        TableColumn<Row, ExportChoice> colExport = new TableColumn<>("Export");
        colExport.setCellValueFactory(cd -> cd.getValue().choice());
        colExport.setCellFactory(col -> new ExportChoiceCell());
        colExport.setPrefWidth(500);

        table.getColumns().setAll(colAlias, colType, colExport);
        table.setItems(buildRows(keystore));

        return table;
    }

    private void bindValueToTable(TableView<Row> table) {
        // ensure map exists
        if (value.get() == null) {
            value.set(FXCollections.observableHashMap());
        }
        // initialize from current items
        value.get().clear();
        for (Row r : table.getItems()) {
            attachRow(r);
        }
        // keep in sync on list changes
        table.getItems().addListener((ListChangeListener<Row>) change -> {
            while (change.next()) {
                if (change.wasRemoved()) {
                    for (Row r : change.getRemoved()) {
                        detachRow(r);
                    }
                }
                if (change.wasAdded()) {
                    for (Row r : change.getAddedSubList()) {
                        attachRow(r);
                    }
                }
            }
        });
    }

    private void attachRow(Row r) {
        // put initial value
        value.get().put(r.alias(), r.choice().get());
        // add listener to keep map updated on selection change
        ChangeListener<ExportChoice> l = (obs, oldV, newV) -> {
            // default to NONE if null
            ExportChoice v = newV != null ? newV : ExportChoice.NONE;
            value.get().put(r.alias(), v);
        };
        rowChoiceListeners.put(r, l);
        r.choice().addListener(l);
    }

    private void detachRow(Row r) {
        // remove listener and map entry
        ChangeListener<ExportChoice> l = rowChoiceListeners.remove(r);
        if (l != null) {
            r.choice().removeListener(l);
        }
        value.get().remove(r.alias());
    }

    /**
     * Builds a list of rows representing entries in a KeyStore.
     * Each row contains the alias, entry type, and export choice for a KeyStore entry.
     *
     * @param keyStoreData the KeyStoreData instance containing the KeyStore to be processed
     * @return an ObservableList of Row objects representing the KeyStore entries
     * @throws GeneralSecurityException if an error occurs while accessing the KeyStore or its entries
     */
    private static ObservableList<Row> buildRows(KeyStoreData keyStoreData) throws GeneralSecurityException {
        // get the selected aliases

        return FXCollections.observableArrayList(
                KeyStoreUtil.listAliases(keyStoreData.keyStore())
                        .stream()
                        .sorted()
                        .map(alias -> {
                            KeyStoreEntryType type = getKeyStoreEntryType(keyStoreData, alias);
                            // default choice depending on type: NONE for all
                            ObjectProperty<ExportChoice> choice = new SimpleObjectProperty<>(ExportChoice.NONE);
                            return new Row(alias, type, choice);
                        })
                        .toList()
        );
    }

    /**
     * A custom TableCell implementation for rendering and managing export choices
     * in a table view. This cell provides an interactive UI element that allows
     * users to choose export options for a specific row in the table.
     * <p>
     * It uses a group of radio buttons, where each radio button corresponds to
     * a different export choice. The choices are dynamically configured and
     * rendered based on the type of the row's data.
     * <p>
     * Features:
     * - Dynamically updates the displayed radio buttons based on the type of the
     *   row's data (e.g., PRIVATE_KEY, SECRET_KEY).
     * - Synchronizes the currently selected export choice with the underlying
     *   data model.
     * - Listens for user interaction to update the model when a different option
     *   is selected.
     * <p>
     * The following export options are supported:
     * - NONE: No export action is performed.
     * - PUBLIC_ONLY: Export only the public key.
     * - PUBLIC_AND_PRIVATE: Export both the public and private keys.
     * - EXPORT: Export key material (for specific types like secret keys).
     * <p>
     * Radio button visibility and grouping are adjusted as follows:
     * - PRIVATE_KEY: Allows selection of NONE, PUBLIC_ONLY, or PUBLIC_AND_PRIVATE.
     * - SECRET_KEY: Allows selection of NONE or EXPORT.
     * - Default: Allows selection of NONE or EXPORT (e.g., for certificates or
     *   unknown types).
     */
    private static class ExportChoiceCell extends TableCell<Row, ExportChoice> {
        private final HBox box;
        private final RadioButton rbNone = new RadioButton(ExportChoice.NONE.label);
        private final RadioButton rbPublicOnly = new RadioButton(ExportChoice.PUBLIC_ONLY.label);
        private final RadioButton rbPublicAndPrivate = new RadioButton(ExportChoice.PUBLIC_AND_PRIVATE.label);
        private final RadioButton rbExport = new RadioButton(ExportChoice.EXPORT.label);
        private final ToggleGroup group = new ToggleGroup();

        ExportChoiceCell() {
            rbNone.setToggleGroup(group);
            rbPublicOnly.setToggleGroup(group);
            rbPublicAndPrivate.setToggleGroup(group);
            rbExport.setToggleGroup(group);
            box = new HBox(10, rbNone, rbPublicOnly, rbPublicAndPrivate, rbExport);
            box.setPadding(new Insets(4, 0, 4, 0));
        }

        @Override
        protected void updateItem(@Nullable ExportChoice item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setGraphic(null);
                return;
            }

            Row row = getTableRow().getItem();
            KeyStoreEntryType type = row.type();

            // configure visible buttons depending on type
            switch (type) {
                case PRIVATE_KEY -> {
                    rbNone.setVisible(true);
                    rbPublicOnly.setVisible(true);
                    rbPublicAndPrivate.setVisible(true);
                    rbExport.setVisible(false);
                    box.getChildren().setAll(rbNone, rbPublicOnly, rbPublicAndPrivate);
                }
                case SECRET_KEY -> {
                    rbNone.setVisible(true);
                    rbPublicOnly.setVisible(false);
                    rbPublicAndPrivate.setVisible(false);
                    rbExport.setVisible(true);
                    box.getChildren().setAll(rbNone, rbExport);
                }
                default -> {
                    // certificates or unknown: allow export (certificate only)
                    rbNone.setVisible(true);
                    rbPublicOnly.setVisible(false);
                    rbPublicAndPrivate.setVisible(false);
                    rbExport.setVisible(true);
                    box.getChildren().setAll(rbNone, rbExport);
                }
            }

            // bind selection to the cell value property
            ExportChoice value = getItem();
            if (value == null) {
                value = ExportChoice.NONE;
            }
            switch (value) {
                case NONE -> group.selectToggle(rbNone);
                case PUBLIC_ONLY -> group.selectToggle(rbPublicOnly);
                case PUBLIC_AND_PRIVATE -> group.selectToggle(rbPublicAndPrivate);
                case EXPORT -> group.selectToggle(rbExport);
            }

            // update model when user changes selection
            group.selectedToggleProperty().addListener((obs, old, nw) -> {
                if (nw == rbNone) {
                    getTableView().getItems().get(getIndex()).choice().set(ExportChoice.NONE);
                } else if (nw == rbPublicOnly) {
                    getTableView().getItems().get(getIndex()).choice().set(ExportChoice.PUBLIC_ONLY);
                } else if (nw == rbPublicAndPrivate) {
                    getTableView().getItems().get(getIndex()).choice().set(ExportChoice.PUBLIC_AND_PRIVATE);
                } else if (nw == rbExport) {
                    getTableView().getItems().get(getIndex()).choice().set(ExportChoice.EXPORT);
                }
            });

            setGraphic(box);
        }
    }

    /**
     * Determines the type of an entry in a KeyStore based on the alias provided.
     *
     * @param keyStore the KeyStoreData instance containing the KeyStore and its access information
     * @param alias the alias identifying the entry within the KeyStore
     * @return the type of the KeyStore entry, represented as a {@link KeyStoreEntryType} enum
     */
    private static KeyStoreEntryType getKeyStoreEntryType(KeyStoreData keyStore, String alias) {
        KeyStore ks = keyStore.keyStore();
        KeyStoreEntryType type;
        try {
            if (ks.isKeyEntry(alias)) {
                // Distinguish between PrivateKey and SecretKey entries if possible
                type = switch (ks.getEntry(alias, new KeyStore.PasswordProtection(keyStore.password().toCharArray()))) {
                    case KeyStore.PrivateKeyEntry _ -> KeyStoreEntryType.PRIVATE_KEY;
                    case KeyStore.SecretKeyEntry _ -> KeyStoreEntryType.SECRET_KEY;
                    default -> KeyStoreEntryType.UNKNOWN;
                };
            } else if (ks.isCertificateEntry(alias)) {
                type = KeyStoreEntryType.CERTIFICTE;
            } else {
                type = KeyStoreEntryType.UNKNOWN;
            }
        } catch (GeneralSecurityException e) {
            LOG.warn("Security error loading keystore entry", e);
            type = KeyStoreEntryType.UNKNOWN;
        }
        return type;
    }

}
