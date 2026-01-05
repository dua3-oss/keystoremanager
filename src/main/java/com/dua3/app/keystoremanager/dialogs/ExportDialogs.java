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
import com.dua3.utility.application.ApplicationUtil;
import com.dua3.utility.crypt.KeyStoreType;
import com.dua3.utility.crypt.KeyStoreUtil;
import com.dua3.utility.fx.controls.Controls;
import com.dua3.utility.i18n.I18N;
import com.dua3.utility.fx.controls.Dialogs;
import com.dua3.utility.fx.controls.WizardDialogBuilder;
import com.dua3.utility.io.IoUtil;
import com.dua3.utility.lang.LangUtil;
import com.dua3.utility.lang.Platform;
import com.dua3.utility.text.MessageFormatter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class ExportDialogs {
    private static final Logger LOG = LogManager.getLogger(ExportDialogs.class);
    private static final I18N I18N = com.dua3.utility.i18n.I18N.getInstance();
    private static final String ID_KEYSTORE_FOLDER = "KEYSTORE_FOLDER";
    private static final String ID_KEYSTORE_NAME = "KEYSTORE_NAME";
    private static final String ID_KEYSTORE_TYPE = "KEYSTORE_TYPE";
    private static final String ID_KEYSTORE_PASSWORD = "KEYSTORE_PASSWORD";
    private static final String SELECTED_ALIASES = "SELECTED_ALIASES";
    private static final String EXPORT_SELECT_ITEMS = "dua3.keystoremanager.dialog.export.page.items_to_export";
    private static final String EXPORT_SETTINGS = "dua3.keystoremanager.dialog.export.page.keystore_settings";
    private static final String EXPORT_TO_FILE = "dua3.keystoremanager.dialog.export.page.export_to_file";
    private static final String EXPORT_TO_FILE_SUCCEEDED = "dua3.keystoremanager.dialog.export.page.exported_to_file";
    private static final String EXPORT_TO_CLIPBOARD = "dua3.keystoremanager.dialog.export.page.export_to_clipboard";

    public enum ExportMode {
        FILE(I18N.get("dua3.keystoremanager.dialog.export.mode.file")),
        CLIPBOARD(I18N.get("dua3.keystoremanager.dialog.export.mode.clipboard"));

        private final String label;
        ExportMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private ExportDialogs() {}
    
    public static void showExportDialog(
            Window owner,
            KeyStoreData keystore
    ) {
        LOG.debug("Showing new export dialog.");

        AtomicReference<ObservableMap<String, KeyStoreExportSelectionInput.ExportChoice>> itemsToExport = new AtomicReference<>();
        AtomicReference<KeyStore> newKeystore = new AtomicReference<>();
        AtomicReference<String> newKeystorePassword = new AtomicReference<>("");
        AtomicReference<Path> newKeystorePath = new AtomicReference<>();
        AtomicReference<KeyStoreType> newKeystoreType = new AtomicReference<>();

        // Create the dialog
        WizardDialogBuilder builder = Dialogs.wizard(owner, MessageFormatter.i18n())
                .title("dua3.keystoremanager.dialog.export.title");

        // *** Select items to export
        KeyStoreExportSelectionInput keyStoreExportSelectionInput = new KeyStoreExportSelectionInput(keystore);

        builder.page(EXPORT_SELECT_ITEMS, Dialogs.inputDialogPane(MessageFormatter.i18n())
                .header("dua3.keystoremanager.dialog.export.header.warning")
                .addInput(SELECTED_ALIASES, (Class<ObservableMap<String, KeyStoreExportSelectionInput.ExportChoice>>) (Class<?>) ObservableMap.class, FXCollections::observableHashMap, keyStoreExportSelectionInput)
                .resultHandler((btn, data) -> {
                    ObservableMap<String, KeyStoreExportSelectionInput.ExportChoice> items = (ObservableMap<String, KeyStoreExportSelectionInput.ExportChoice>) data.get(SELECTED_ALIASES);
                    itemsToExport.set(items);
                    return !items.isEmpty();
                })
        );

        // *** Keystore Settings
        builder.page(EXPORT_SETTINGS, Dialogs.inputDialogPane(MessageFormatter.i18n())
                .header("dua3.keystoremanager.dialog.export.header.settings")
                .inputComboBox(ID_KEYSTORE_TYPE, "dua3.keystoremanager.dialog.export.type", () -> KeyStoreType.PKCS12, KeyStoreType.class, List.of(KeyStoreType.values()))
                .inputPasswordWithVerification(ID_KEYSTORE_PASSWORD, "dua3.keystoremanager.dialog.export.password", "dua3.keystoremanager.dialog.export.password.repeat")
                .resultHandler((btn, data) -> createKeyStoreForExport(owner, keystore, data, itemsToExport, newKeystorePassword, newKeystore, newKeystoreType))
                .next(Map.of(
                        new ButtonType("Export to File"), EXPORT_TO_FILE,
                        new ButtonType("Export to Clipboard"), EXPORT_TO_CLIPBOARD
                ))
        );

        // *** Export to file
        builder.page(EXPORT_TO_FILE, Dialogs.inputDialogPane(MessageFormatter.i18n())
                .header("dua3.keystoremanager.dialog.export.header.export_to_file")
                .inputFolder(ID_KEYSTORE_FOLDER, "dua3.keystoremanager.dialog.export.target_folder", keystore.path()::getParent, true)
                .inputString(ID_KEYSTORE_NAME, "dua3.keystoremanager.dialog.export.keystore_name", () -> null, v -> IoUtil.isValidFileName(v, Platform.currentPlatform()) ? Optional.empty() : Optional.of("Input a valid filename"))
                .resultHandler((btn, data) -> {
                    if (!(data.get(ID_KEYSTORE_FOLDER) instanceof Path folder)) {
                        return false;
                    }
                    String name = Objects.requireNonNullElse(data.get(ID_KEYSTORE_NAME), "").toString();
                    if (!IoUtil.isValidFileName(name, Platform.currentPlatform())) {
                        return false;
                    }
                    Path keystorePath = folder.resolve(name + "." + newKeystoreType.get().getExtension());
                    newKeystorePath.set(keystorePath);
                    try (OutputStream out = Files.newOutputStream(keystorePath)) {
                        newKeystore.get().store(out, newKeystorePassword.get().toCharArray());
                    } catch (IOException | GeneralSecurityException e) {
                        LOG.warn("Error writing keystore to file", e);
                        Dialogs.alert(owner, Alert.AlertType.ERROR, MessageFormatter.i18n())
                                .title("dua3.keystoremanager.dialog.export.error.keystore_write_failed.title")
                                .header("dua3.keystoremanager.dialog.export.error.keystore_write_failed.header")
                                .text(MessageFormatter.literal(e.getMessage()))
                                .showAndWait();
                        return false;
                    }
                    return true;
                })
                .next(EXPORT_TO_FILE_SUCCEEDED)
        );

        // *** Exported to file
        builder.page(EXPORT_TO_FILE_SUCCEEDED, Dialogs.inputDialogPane(MessageFormatter.i18n())
                .header("dua3.keystoremanager.dialog.export.header.export_to_file_succeeded")
                .node(createButtonsExportToFile(newKeystorePassword, newKeystorePath))
                .next(Map.of(ButtonType.FINISH, ""))
        );

        // *** Export to clipboard
        builder.page(EXPORT_TO_CLIPBOARD, Dialogs.inputDialogPane(MessageFormatter.i18n())
                .header("dua3.keystoremanager.dialog.export.header.export_to_clipboard")
                .node(createButtonsExportToClipBoard(owner, keystore, newKeystorePassword))
                .next(Map.of(ButtonType.FINISH, ""))
        );

        // show the wizard
        builder.showAndWait();
    }

    private static VBox createButtonsExportToClipBoard(Window owner, KeyStoreData keystore, AtomicReference<String> newKeystorePassword) {
        VBox vBox = new VBox(
                10,
                createButtonCopyKeyStoreToClipBoard(owner, keystore, newKeystorePassword),
                createButtonCopyPasswordToClipboard(newKeystorePassword)
        );
        vBox.setFillWidth(true);
        return vBox;
    }

    private static VBox createButtonsExportToFile(AtomicReference<String> newKeystorePassword, AtomicReference<Path> newKeystorePath) {
        VBox vBox = new VBox(
                10,
                createButtonCopyPasswordToClipboard(newKeystorePassword),
                createButtonShowInFileManager(newKeystorePath)
        );
        vBox.setFillWidth(true);
        return vBox;
    }

    private static boolean createKeyStoreForExport(
            Window owner,
            KeyStoreData keystoreToExport,
            Map<String, Object> data,
            AtomicReference<ObservableMap<String, KeyStoreExportSelectionInput.ExportChoice>> itemsToExport,
            AtomicReference<String> dstKeystorePassword,
            AtomicReference<KeyStore> newKeystore,
            AtomicReference<KeyStoreType> newKeystoreType) {
        try {
            KeyStoreType type = (KeyStoreType) data.get(ID_KEYSTORE_TYPE);
            dstKeystorePassword.set(data.get(ID_KEYSTORE_PASSWORD).toString());
            KeyStore dstKeyStore = KeyStoreUtil.createKeyStore(type, dstKeystorePassword.get().toCharArray());
            newKeystore.set(dstKeyStore);
            newKeystoreType.set(type);

            // add selected entries to new keystore
            KeyStore srcKeyStore = keystoreToExport.keyStore();
            char[] srcPassword = keystoreToExport.password().toCharArray();
            char[] dstPassword = dstKeystorePassword.get().toCharArray();

            for (var entry : itemsToExport.get().entrySet()) {
                String alias = entry.getKey();
                KeyStoreExportSelectionInput.ExportChoice choice = entry.getValue();
                switch (choice) {
                    case NONE -> {
                        // do nothing
                    }
                    case PUBLIC_ONLY -> {
                        // Export only the public certificate for key pairs
                        Certificate cert = srcKeyStore.getCertificate(alias);
                        if (cert != null) {
                            dstKeyStore.setCertificateEntry(alias, cert);
                        } else if (srcKeyStore.isCertificateEntry(alias)) {
                            // certificate entry fallback (should already be covered by cert != null)
                            Certificate c = srcKeyStore.getCertificate(alias);
                            if (c != null) {
                                dstKeyStore.setCertificateEntry(alias, c);
                            }
                        }
                    }
                    case PUBLIC_AND_PRIVATE -> {
                        // Export private key with its certificate chain
                        KeyStore.Entry e = srcKeyStore.getEntry(alias, new KeyStore.PasswordProtection(srcPassword));
                        if (e instanceof KeyStore.PrivateKeyEntry pke) {
                            Certificate[] chain = pke.getCertificateChain();
                            if (chain == null || chain.length == 0) {
                                Certificate cert = srcKeyStore.getCertificate(alias);
                                if (cert != null) {
                                    chain = new Certificate[]{cert};
                                }
                            }
                            dstKeyStore.setKeyEntry(alias, pke.getPrivateKey(), dstPassword, chain);
                        } else if (srcKeyStore.isKeyEntry(alias)) {
                            // Not a private key entry; ignore for this choice
                            LOG.warn("Alias '{}' is not a PrivateKeyEntry; skipping PUBLIC_AND_PRIVATE export.", alias);
                        }
                    }
                    case EXPORT -> {
                        // Export full material where appropriate:
                        // - For SecretKey entries: copy the secret key (re-protect with destination password)
                        // - For Certificate entries: copy the certificate
                        // - For other types: best-effort certificate export
                        if (srcKeyStore.isKeyEntry(alias)) {
                            KeyStore.Entry e = srcKeyStore.getEntry(alias, new KeyStore.PasswordProtection(srcPassword));
                            switch (e) {
                                case KeyStore.SecretKeyEntry ske ->
                                    // Re-wrap the secret key with the destination keystore password
                                        dstKeyStore.setEntry(alias, new KeyStore.SecretKeyEntry(ske.getSecretKey()), new KeyStore.PasswordProtection(dstPassword));
                                case KeyStore.PrivateKeyEntry privateKeyEntry -> {
                                    // For private keys, EXPORT in the UI is not offered; fall back to certificate only for safety
                                    Certificate cert = srcKeyStore.getCertificate(alias);
                                    if (cert != null) {
                                        dstKeyStore.setCertificateEntry(alias, cert);
                                    }
                                }
                                case null, default -> {
                                    // Unknown key entry type; attempt certificate copy
                                    Certificate cert = srcKeyStore.getCertificate(alias);
                                    if (cert != null) {
                                        dstKeyStore.setCertificateEntry(alias, cert);
                                    }
                                }
                            }
                        } else if (srcKeyStore.isCertificateEntry(alias)) {
                            Certificate cert = srcKeyStore.getCertificate(alias);
                            if (cert != null) {
                                dstKeyStore.setCertificateEntry(alias, cert);
                            }
                        } else {
                            LOG.warn("Alias '{}' is neither key nor certificate entry; skipping EXPORT.", alias);
                        }
                    }
                }
            }
            return true;
        } catch (GeneralSecurityException e) {
            LOG.warn("Error creating keystore", e);
            Dialogs.alert(owner, Alert.AlertType.ERROR, MessageFormatter.i18n())
                    .title("dua3.keystoremanager.dialog.export.error.keystore_creation_failed.title")
                    .header("dua3.keystoremanager.dialog.export.error.keystore_creation_failed.header")
                    .text(MessageFormatter.literal(e.getMessage()))
                    .showAndWait();
            return false;
        }
    }

    /**
     * Creates a button that, when clicked, opens the file specified by the provided path in the
     * appropriate file manager. The behavior varies depending on the operating system:
     * - On macOS, it opens the Finder and highlights the file.
     * - On Windows, it opens File Explorer and selects the file.
     * - On other platforms, it opens the parent directory in the default file manager.
     *
     * @param path an {@code AtomicReference<Path>} object representing the path to the file that should
     *             be shown in the file manager
     * @return a {@code Button} instance configured to perform the file manager display action when clicked
     */
    private static Button createButtonShowInFileManager(AtomicReference<Path> path) {
        String buttonText = I18N.format("dua3.keystoremanager.export.button.show_in_file_manager", ApplicationUtil.getLocalizedFileManagerName(I18N));

        return Controls.button()
                .text(buttonText)
                .action(() -> ApplicationUtil.showInFileManager(path.get()))
                .maxWidth(Integer.MAX_VALUE)
                .build();
    }

    /**
     * Creates a button that allows the user to copy the provided KeyStore data to the system clipboard.
     * The KeyStore data is serialized into a Base64-encoded string and placed on the clipboard.
     *
     * @param owner the window that owns the dialog or UI component for error handling
     * @param keystore a {@code KeyStoreData} object containing the KeyStore instance, its password, and file path
     * @param newKeystorePassword an {@code AtomicReference} holding the new password for the KeyStore
     * @return a {@code Button} instance configured to copy the KeyStore to the clipboard when clicked
     */
    private static Button createButtonCopyKeyStoreToClipBoard(Window owner, KeyStoreData keystore, AtomicReference<String> newKeystorePassword) {
        return Controls.button()
                .text("Copy Keystore to Clipboard").action(() -> {
                    try {
                        String dstPassword = newKeystorePassword.get();
                        // copy to clipboard
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        keystore.keyStore().store(baos, dstPassword.toCharArray());
                        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

                        ClipboardContent content = new ClipboardContent();
                        content.putString(base64);
                        Clipboard.getSystemClipboard().setContent(content);
                    } catch (GeneralSecurityException | IOException e) {
                        LOG.warn("Error copying keystore to clipboard", e);
                        Dialogs.alert(owner, Alert.AlertType.ERROR, MessageFormatter.i18n())
                                .title("dua3.keystoremanager.dialog.export.error.clipboard.title")
                                .header("dua3.keystoremanager.dialog.export.error.clipboard.header")
                                .text(MessageFormatter.literal(e.getMessage()))
                                .showAndWait();
                    }
                })
                .maxWidth(Integer.MAX_VALUE)
                .build();
    }

    /**
     * Creates a button that copies the provided keystore password to the system clipboard.
     *
     * @param newKeystorePassword an {@code AtomicReference} holding the new keystore password to be copied
     * @return a {@code Button} instance configured to copy the password to the clipboard when clicked
     */
    private static Button createButtonCopyPasswordToClipboard(AtomicReference<String> newKeystorePassword) {
        return Controls.button()
                .text("Copy Password to Clipboard")
                .action(() -> {
                    String password = newKeystorePassword.get();
                    // copy to clipboard
                    ClipboardContent content = new ClipboardContent();
                    content.putString(password);
                    Clipboard.getSystemClipboard().setContent(content);
                })
                .maxWidth(Integer.MAX_VALUE)
                .build();
    }

}