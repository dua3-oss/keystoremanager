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
import com.dua3.utility.i18n.I18N;
import com.dua3.utility.crypt.KeyStoreType;
import com.dua3.utility.crypt.KeyUtil;
import com.dua3.utility.fx.controls.Dialogs;
import com.dua3.utility.fx.controls.FileDialogMode;
import com.dua3.utility.fx.controls.InputDialogBuilder;
import com.dua3.utility.fx.controls.InputResult;
import com.dua3.utility.fx.controls.InputValidatorFactory;
import com.dua3.utility.text.MessageFormatter;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A utility class for displaying dialogs related to KeyStore operations such as creating new KeyStores,
 * viewing detailed information about KeyStore entries, and handling unknown KeyStore entries.
 * Provides static methods to interact with UI components for user input and feedback.
 */
public final class KeyStoreDialogs {
    private static final Logger LOG = LogManager.getLogger(KeyStoreDialogs.class);
    private static final I18N I18N = com.dua3.utility.i18n.I18N.getInstance();

    /**
     * ID of the folder input field.
     */
    public static final String ID_FOLDER = "FOLDER";
    /**
     * ID of the name input field.
     */
    public static final String ID_NAME = "NAME";
    /**
     * ID of the type input field.
     */
    public static final String ID_KEY_TYPE = "TYPE";
    /**
     * ID of the password input field.
     */
    public static final String ID_PASSWORD = "PASSWORD";

    /**
     * A constant string representation of a placeholder value for sensitive information.
     */
    public static final String HIDDEN = I18N.get("dua3.keystoremanager.dialog.details.hidden");

    private KeyStoreDialogs() {}

    /**
     * Displays a dialog for creating a new KeyStore, allowing the user to specify a folder, name,
     * KeyStore type, and password. Validates user inputs for correctness, including password strength
     * and confirmation checks.
     *
     * @param owner the parent Window that owns the displayed dialog
     * @param initialFolder a Supplier providing the initial folder path for the file dialog
     * @return an Optional containing a Map of the collected input values if the dialog is successfully
     * completed by the user, or an empty Optional if the dialog is canceled
     */
    public static Optional<InputResult> showCreateNewKeyStoreDialog(Window owner, Supplier<Path> initialFolder) {
        InputValidatorFactory vf = new InputValidatorFactory(MessageFormatter.standard());

        return Dialogs.input(owner)
                .title(I18N.get("dua3.keystoremanager.dialog.create_keystore.title"))
                .inputFile(ID_FOLDER, I18N.get("dua3.keystoremanager.dialog.create_keystore.folder"), initialFolder, FileDialogMode.DIRECTORY, true, Collections.emptyList(), vf.directory(I18N.get("dua3.keystoremanager.dialog.create_keystore.folder.select")))
                .inputString(ID_NAME, I18N.get("dua3.keystoremanager.dialog.create_keystore.name"), () -> "", vf.nonEmpty(I18N.get("dua3.keystoremanager.dialog.create_keystore.name.required")))
                .inputComboBox(ID_KEY_TYPE, I18N.get("dua3.keystoremanager.dialog.create_keystore.type"), () -> KeyStoreType.PKCS12, KeyStoreType.class, List.of(KeyStoreType.valuesReadble()))
                .inputPasswordWithVerification(ID_PASSWORD, I18N.get("dua3.keystoremanager.dialog.create_keystore.password"), I18N.get("dua3.keystoremanager.dialog.create_keystore.password.repeat"))
                .showAndWait();
    }

    /**
     * Displays detailed information about a specific entry in a KeyStore.
     *
     * @param owner    the parent Window that owns the displayed dialogs
     * @param keyStore the KeyStoreData containing the KeyStore and associated metadata
     * @param alias    the alias of the entry within the KeyStore to retrieve and display
     */
    public static void showDetails(Window owner, KeyStoreData keyStore, String alias) {
        LOG.debug("Showing details for alias: {}", alias);

        KeyStore ks = keyStore.keyStore();

        InputDialogBuilder builder = Dialogs.input(owner, MessageFormatter.i18n())
                .title("dua3.keystoremanager.dialog.details.title")
                .resizable(true);

        try {
            builder.section(0, "dua3.keystoremanager.dialog.details.private_key_details");

            // Get certificate information
            java.security.cert.Certificate cert = ks.getCertificate(alias);
            if (cert != null) {
                PublicKey publicKey = cert.getPublicKey();

                int keySize = switch (publicKey) {
                    case java.security.interfaces.RSAKey k -> k.getModulus().bitLength();
                    case java.security.interfaces.DSAKey k -> k.getParams().getP().bitLength();
                    case java.security.interfaces.ECKey k -> k.getParams().getCurve().getField().getFieldSize();
                    default -> -1;
                };

                // --- Certificate data ---

                builder.section(1, "dua3.keystoremanager.dialog.details.certificate");

                builder.labeledText("dua3.keystoremanager.dialog.details.type", "\0", cert.getType());
                builder.labeledText("dua3.keystoremanager.dialog.details.algorithm", "\0", publicKey.getAlgorithm());
                if (keySize >= 0) {
                    builder.labeledText("dua3.keystoremanager.dialog.details.key_size", "dua3.keystoremanager.dialog.details.key_size.format", keySize);
                }

                if (cert instanceof X509Certificate x509Cert) {
                    builder.labeledText("dua3.keystoremanager.dialog.details.valid_from", "\0", x509Cert.getNotBefore());
                    builder.labeledText("dua3.keystoremanager.dialog.details.valid_until", "\0", x509Cert.getNotAfter());

                    // Add subject fields to table if it's an X509Certificate
                    builder.section(2, "dua3.keystoremanager.dialog.details.subject_fields");
                    String subjectDN = x509Cert.getSubjectX500Principal().getName();
                    String[] subjectParts = subjectDN.split(",");
                    for (String part : subjectParts) {
                        String[] keyValue = part.trim().split("=", 2);
                        if (keyValue.length == 2) {
                            builder.labeledText("\0" + keyValue[0], "\0", keyValue[1]);
                        }
                    }
                }

                // --- Public Key data ---

                builder.section(1, "dua3.keystoremanager.dialog.details.public_key");

                TextArea nodePublicKeyPem = new TextArea(KeyUtil.toPem(publicKey));
                nodePublicKeyPem.setEditable(false);
                nodePublicKeyPem.setWrapText(true);
                nodePublicKeyPem.setPrefRowCount(5);
                builder.node("dua3.keystoremanager.dialog.details.public_key", nodePublicKeyPem);
            }

            // ===== SECTION 3: Private Key =====

            builder.section(1, "dua3.keystoremanager.dialog.details.private_key");

            TextArea nodePrivateKeyPem = new TextArea(HIDDEN);
            nodePrivateKeyPem.setEditable(false);
            nodePrivateKeyPem.setWrapText(true);
            nodePrivateKeyPem.setPrefRowCount(5);

            Button btnShowPrivateKey = new Button(I18N.get("dua3.keystoremanager.dialog.details.show_private_key"));
            btnShowPrivateKey.setOnAction(evt -> {
                if (nodePrivateKeyPem.getText().equals(HIDDEN)) {
                    Optional<String> password = Dialogs.input(owner, MessageFormatter.i18n())
                            .title("dua3.keystoremanager.dialog.details.unlock_private_key.title")
                            .header("dua3.keystoremanager.dialog.details.unlock_private_key.header")
                            .inputPassword("password", "dua3.keystoremanager.dialog.details.unlock_private_key.password", keyStore::password)
                            .showAndWait()
                            .map(r -> String.valueOf(r.get("password")));

                    password.ifPresent(p -> {
                        try {
                            java.security.Key key = ks.getKey(alias, p.toCharArray());
                            if (key instanceof java.security.PrivateKey pk) {
                                nodePrivateKeyPem.setText(KeyUtil.toPem(pk));
                                btnShowPrivateKey.setText(I18N.get("dua3.keystoremanager.dialog.details.hide_private_key"));
                            } else {
                                Dialogs.alert(owner, Alert.AlertType.ERROR)
                                        .title("dua3.keystoremanager.dialog.details.error")
                                        .header("dua3.keystoremanager.dialog.details.error.retrieve_private_failed")
                                        .text("dua3.keystoremanager.dialog.details.error.not_a_private_key", alias)
                                        .showAndWait();
                            }
                        } catch (Exception e) {
                            LOG.warn("Could not retrieve private key for '{}'", alias, e);
                            Dialogs.alert(owner, Alert.AlertType.ERROR)
                                    .title("dua3.keystoremanager.dialog.details.error")
                                    .header("dua3.keystoremanager.dialog.details.error.retrieve_private_failed")
                                    .text("dua3.keystoremanager.dialog.details.error.check_password", e.getMessage())
                                    .showAndWait();
                        }
                    });
                } else {
                    nodePrivateKeyPem.setText(HIDDEN);
                    btnShowPrivateKey.setText(I18N.get("dua3.keystoremanager.dialog.details.show_private_key"));
                }
            });

            builder.node("dua3.keystoremanager.dialog.details.private_key", nodePrivateKeyPem);
            builder.node("", btnShowPrivateKey);

            // show certificate chain
            try {
                java.security.cert.Certificate[] chain = ks.getCertificateChain(alias);
                if ((chain == null || chain.length == 0) && cert != null) {
                    chain = new java.security.cert.Certificate[]{cert};
                }

                builder.section(2, "dua3.keystoremanager.dialog.details.certificate_chain");

                if (chain != null && chain.length > 0) {
                    for (int i = 0; i < chain.length; i++) {
                        String label = "\0'#" + (i + 1) + "'";
                        if (chain[i] instanceof X509Certificate x509) {
                            builder.labeledText(label, "dua3.keystoremanager.dialog.details.certificate_chain.format",
                                    x509.getSubjectX500Principal().getName(),
                                    x509.getIssuerX500Principal().getName(),
                                    x509.getNotBefore(), x509.getNotAfter()
                            );
                        } else {
                            builder.labeledText(label, "dua3.keystoremanager.dialog.details.certificate_chain.other_type", chain[i].getType());
                        }
                    }
                } else {
                    builder.text("dua3.keystoremanager.dialog.details.certificate_chain.none");
                }
            } catch (Exception e) {
                LOG.warn("Could not build certificate chain preview for '{}': {}", alias, e.toString());
            }

            builder.buttons(ButtonType.OK);
            builder.showAndWait();
        } catch (KeyStoreException e) {
            LOG.warn("Error retrieving key details for alias: {}", alias, e);
            Dialogs.alert(owner, Alert.AlertType.WARNING)
                    .title("dua3.keystoremanager.dialog.details.error.retrieving_key_details")
                    .header("dua3.keystoremanager.dialog.details.error.retrieving_key_details_for_alias", alias)
                    .text("%s", e.getMessage())
                    .showAndWait();
        }
    }

    /**
     * Displays details of a KeyStore entry with an unknown type in a dialog.
     *
     * @param owner the parent Window that owns the displayed dialog
     * @param type the KeyStoreEntryType representing the type of the entry
     * @param alias the alias of the specific entry within the KeyStore
     */
    public static void showUnknownEntryDetails(Window owner, KeyStoreEntryType type, String alias) {
        Dialogs.input(owner)
                .title("dua3.keystoremanager.dialog.details.unknown_entry_details")
                .labeledText("dua3.keystoremanager.dialog.details.alias", "%s", alias)
                .labeledText("dua3.keystoremanager.dialog.details.type", "%s", type)
                .showAndWait();
    }
}
