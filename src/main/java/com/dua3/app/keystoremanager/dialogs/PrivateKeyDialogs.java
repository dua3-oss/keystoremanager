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

import com.dua3.utility.crypt.AsymmetricAlgorithm;
import com.dua3.utility.crypt.KeyStoreUtil;
import com.dua3.utility.i18n.I18N;
import com.dua3.utility.fx.controls.Dialogs;
import com.dua3.utility.fx.controls.InputDialogBuilder;
import com.dua3.utility.fx.controls.InputResult;
import com.dua3.utility.fx.controls.InputValidatorFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A utility class providing secret key related dialogs.
 */
public final class PrivateKeyDialogs {
    private static final Logger LOG = LogManager.getLogger(PrivateKeyDialogs.class);
    private static final I18N I18N = com.dua3.utility.i18n.I18N.getInstance();

    private PrivateKeyDialogs() {}

    /**
     * The Fields enumeration defines a set of standard certificate fields used for
     * creating or managing certificates. Each field contains metadata, such as its
     * identifier, display label, data type, and whether the field is required.
     */
    public enum Fields {
        /**
         * Represents the alias field in a certificate.
         */
        ALIAS("ALIAS", I18N.get("dua3.keystoremanager.dialog.new_private_key.field.alias.label"), String.class, true, I18N.get("dua3.keystoremanager.dialog.new_private_key.field.alias.description")),
        /**
         * Represents the algorithm to use.
         */
        ALGORITHM("ALGORITHM", I18N.get("dua3.keystoremanager.dialog.new_private_key.field.algorithm.label"), AsymmetricAlgorithm.class, true, I18N.get("dua3.keystoremanager.dialog.new_private_key.field.algorithm.description")),
        /**
         * Represents the key size in bits.
         */
        KEY_SIZE("KEY_SIZE", I18N.get("dua3.keystoremanager.dialog.new_private_key.field.key_size.label"), Integer.class, true, I18N.get("dua3.keystoremanager.dialog.new_private_key.field.key_size.description")),
        /**
         * Represents the number of days for which a certificate is valid.
         */
        VALID_DAYS("VALID", I18N.get("dua3.keystoremanager.dialog.new_private_key.field.valid_days.label"), Integer.class, true, I18N.get("dua3.keystoremanager.dialog.new_private_key.field.valid_days.description")),
        /**
         * Represents the ability to sign sub-certificates.
         */
        ENABLE_CA("ENABLE_CA", I18N.get("dua3.keystoremanager.dialog.new_private_key.field.enable_ca.label"), Boolean.class, true, I18N.get("dua3.keystoremanager.dialog.new_private_key.field.enable_ca.description")),
        /**
         * Represents the "Common Name (CN)" field in a certificate.
         */
        COMMON_NAME("CN", I18N.get("dua3.keystoremanager.dialog.new_private_key.field.common_name.label"), String.class, true, I18N.get("dua3.keystoremanager.dialog.new_private_key.field.common_name.description")),
        /**
         * Represents the "Organization" field in a certificate.
         */
        ORGANIZATION("O", I18N.get("dua3.keystoremanager.dialog.new_private_key.field.organization.label"), String.class, true, I18N.get("dua3.keystoremanager.dialog.new_private_key.field.organization.description")),
        /**
         * Represents the Organizational Unit (OU) field in a certificate.
         */
        ORGANIZATIONAL_UNIT("OU", I18N.get("dua3.keystoremanager.dialog.new_private_key.field.organizational_unit.label"), String.class, false, I18N.get("dua3.keystoremanager.dialog.new_private_key.field.organizational_unit.description")),
        /**
         * Represents the Country (C) field in a certificate.
         */
        COUNTRY("C", I18N.get("dua3.keystoremanager.dialog.new_private_key.field.country.label"), String.class, true, I18N.get("dua3.keystoremanager.dialog.new_private_key.field.country.description")),
        /**
         * Represents the State/Province (ST) field in a certificate.
         */
        STATE("ST", I18N.get("dua3.keystoremanager.dialog.new_private_key.field.state.label"), String.class, false, I18N.get("dua3.keystoremanager.dialog.new_private_key.field.state.description")),
        /**
         * Represents the Locality (L) field in a certificate.
         */
        LOCALITY("L", I18N.get("dua3.keystoremanager.dialog.new_private_key.field.locality.label"), String.class, false, I18N.get("dua3.keystoremanager.dialog.new_private_key.field.locality.description")),
        /**
         * Represents the "EMAIL" field in a certificate.
         */
        EMAIL("EMAIL", I18N.get("dua3.keystoremanager.dialog.new_private_key.field.email.label"), String.class, false, I18N.get("dua3.keystoremanager.dialog.new_private_key.field.email.description"));

        /**
         * Represents the ID for the certificate field.
         */
        public final String id;
        /**
         * Represents the label text for the certificate field.
         */
        public final String label;
        /**
         * Represents the data type of the certificate field.
         */
        public final Class<?> type;
        /**
         * Indicates that the field is required in a certificate.
         */
        public final boolean isRequired;
        /**
         * Represents the description text for the certificate field.
         */
        public final String description;

        /**
         * Returns the description text for this field.
         *
         * @return the description text
         */
        public String description() {
            return description;
        }

        Fields(String id, String label, Class<?> type, boolean required, String descripttion) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.isRequired = required;
            this.description = descripttion;
        }
    }

    /**
     * ID for the parent key input.
     */
    public static final String ID_PARENT = "PARENT";

    /**
     * Builds and shows the input dialog for creating a new certificate.
     * The dialog includes options for selecting parent certificates,
     * choosing cryptographic algorithms, specifying key sizes, enabling certificate authority,
     * and setting validity duration. Additionally, standard certificate fields can be filled
     * in the dialog as required or optional inputs.
     *
     * @param owner           the owner window
     * @param ks              the KeyStore containing existing certificates, used to populate parent certificate options
     * @param aliasStandAlone the alias of the current standalone certificate, used to exclude from parent options
     * @param vf              the factory for creating input validators to validate user inputs in the dialog
     * @return an Optional containing the result map or an empty Optional if the dialog was cancelled
     */
    public static Optional<InputResult> showNewPrivateKeyDialog(Window owner, KeyStore ks, String aliasStandAlone, InputValidatorFactory vf) {
        LOG.debug("Showing new private key dialog.");

        ObservableList<Integer> keySizes = FXCollections.observableArrayList();

        List<String> parentAliases = new ArrayList<>();
        parentAliases.add(aliasStandAlone);
        try {
            parentAliases.addAll(KeyStoreUtil.getCaAliases(ks));
        } catch (KeyStoreException e) {
            Dialogs.alert(owner, Alert.AlertType.ERROR)
                    .title(I18N.get("dua3.keystoremanager.dialog.new_private_key.error.retrieve_aliases.title"))
                    .header(I18N.get("dua3.keystoremanager.dialog.new_private_key.error.retrieve_aliases.header"))
                    .text(e.getMessage())
                    .showAndWait();
            return Optional.empty();
        }

        InputDialogBuilder builder = Dialogs.input(owner)
                .title(I18N.get("dua3.keystoremanager.dialog.new_private_key.title"))
                .header(I18N.get("dua3.keystoremanager.dialog.new_private_key.header"))
                .inputComboBox(ID_PARENT, I18N.get("dua3.keystoremanager.dialog.new_private_key.field.parent.label"), () -> aliasStandAlone, parentAliases);

        // add fields
        Stream.of(Fields.values())
                .forEach(field -> {
                    switch (field) {
                        case KEY_SIZE -> builder.inputComboBox(field.id, field.label, () -> 2048, keySizes);
                        case ALGORITHM -> builder.inputComboBox(field.id, field.label, () -> AsymmetricAlgorithm.RSA,
                                AsymmetricAlgorithm.class,
                                algorithm -> {
                                    keySizes.setAll(algorithm == null ? Collections.emptyList() : algorithm.getSupportedKeySizes());
                                    return algorithm == null ? Optional.of(I18N.get("dua3.keystoremanager.dialog.new_private_key.validator.select_algorithm")) : Optional.empty();
                                });
                        default -> {
                            switch (field.type.getName()) {
                                case "java.lang.String" -> builder.inputString(
                                        field.id,
                                        field.label,
                                        () -> "",
                                        field.isRequired ? vf.nonBlank(I18N.get("dua3.keystoremanager.dialog.new_private_key.validator.required"), field.label) : vf.noCheck()
                                );
                                case "java.lang.Integer" -> builder.inputInteger(
                                        field.id,
                                        field.label,
                                        () -> null,
                                        field.isRequired ? vf.nonNull(I18N.get("dua3.keystoremanager.dialog.new_private_key.validator.required"), field.label) : vf.noCheck()
                                );
                                case "java.lang.Boolean" -> builder.inputCheckBox(
                                        field.id,
                                        field.label,
                                        () -> Boolean.FALSE,
                                        field.description()
                                );
                                default -> {
                                    if (field.type.isEnum()) {
                                        builder.inputComboBox(
                                                field.id,
                                                field.label,
                                                () -> null,
                                                (Class<Enum>) field.type
                                        );
                                    } else {
                                        throw new IllegalStateException("Unexpected type: " + field.type.getSimpleName());
                                    }
                                }
                            }
                        }
                    }
                });

        return builder.showAndWait();
    }
}
