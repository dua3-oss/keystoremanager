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

import com.dua3.app.keystoremanager.dialogs.ExportDialogs;
import com.dua3.app.keystoremanager.dialogs.KeyStoreDialogs;
import com.dua3.app.keystoremanager.dialogs.PrivateKeyDialogs;
import com.dua3.app.keystoremanager.dialogs.SecretKeyDialogs;
import com.dua3.utility.i18n.I18N;
import com.dua3.utility.application.ApplicationUtil;
import com.dua3.utility.crypt.AsymmetricAlgorithm;
import com.dua3.utility.crypt.CertificateUtil;
import com.dua3.utility.crypt.KeyStoreType;
import com.dua3.utility.crypt.KeyStoreUtil;
import com.dua3.utility.crypt.KeyUtil;
import com.dua3.utility.crypt.SymmetricAlgorithm;
import com.dua3.utility.data.DataUtil;
import com.dua3.utility.fx.FxImage;
import com.dua3.utility.fx.FxImageUtil;
import com.dua3.utility.fx.controls.Controls;
import com.dua3.utility.fx.controls.Dialogs;
import com.dua3.utility.fx.controls.InputResult;
import com.dua3.utility.fx.controls.InputValidatorFactory;
import com.dua3.utility.fx.controls.PromptMode;
import com.dua3.utility.io.IoUtil;
import com.dua3.utility.lang.LangUtil;
import com.dua3.utility.lang.WrappedException;
import com.dua3.utility.text.MessageFormatter;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.SelectionMode;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A custom JavaFX component for managing and displaying information about a KeyStore.
 * The component provides functionality to create or load a KeyStore, depending on its current state.
 * If a KeyStore is not loaded, the pane shows options to create or load a KeyStore.
 * When a KeyStore is loaded, the pane displays its basic information.
 */
public class KeyStorePane extends Pane {

    private static final Logger LOG = LogManager.getLogger(KeyStorePane.class);
    private static final I18N I18N = com.dua3.utility.i18n.I18N.getInstance();

    private static final String LOGO_PATH = "/com/dua3/app/keystoremanager/logo-256.png";
    private static final FxImage LOGO;
    private static final String EMPTY_NAME = I18N.get("dua3.keystoremanager.pane.empty_name");
    private static final String ERROR_CREATING_PRIVATE_KEY = I18N.get("dua3.keystoremanager.pane.error.creating_private_key");
    private static final String ERROR_CREATING_PUBLIC_KEY = I18N.get("dua3.keystoremanager.pane.error.creating_public_key");

    private final KeyStoreManager manager;

    static {
        try {
            LOGO = (FxImage) FxImageUtil.getInstance().load(LangUtil.getResourceURL(KeyStorePane.class, LOGO_PATH));
        } catch (IOException e) {
            LOG.error("could not load logo from {}", LOGO_PATH, e);
            throw new UncheckedIOException(e);
        }
    }

    private static final FileChooser.ExtensionFilter[] EXTENSION_FILTERS = {
            new FileChooser.ExtensionFilter(I18N.get("dua3.keystoremanager.pane.filter.keystore_files"), "*.p12", "*.pfx", "*.jks", "*.jceks"),
            new FileChooser.ExtensionFilter(I18N.get("dua3.keystoremanager.pane.filter.p12"), "*.p12", "*.pfx"),
            new FileChooser.ExtensionFilter(I18N.get("dua3.keystoremanager.pane.filter.jks"), "*.jks"),
            new FileChooser.ExtensionFilter(I18N.get("dua3.keystoremanager.pane.filter.jceks"), "*.jceks")
    };

    private @Nullable KeyStoreData keyStore;
    private final StringProperty name = new SimpleStringProperty(this, "name", "");
    private final ObservableList<EntryRow> keyStoreItems = FXCollections.observableArrayList();

    /**
     * Constructs a new instance of KeyStorePane with the provided KeyStore data.
     * If the provided KeyStore data is null, the pane will display empty content.
     *
     * @param manager  the {@link KeyStoreManager} instance this pane belongs to
     * @param keyStore the KeyStoreData to be associated with this pane,
     *                 or null if no KeyStore data is provided
     */
    public KeyStorePane(KeyStoreManager manager, @Nullable KeyStoreData keyStore) {
        LOG.debug("new KeyStorePane({})", keyStore);
        this.manager = manager;
        this.keyStore = keyStore;
        updateContent();
    }


    /**
     * Provides a read-only property representing the name of the KeyStore.
     *
     * @return a {@code ReadOnlyStringProperty} that contains the name of the KeyStore
     */
    public ReadOnlyStringProperty nameProperty() {
        return name;
    }

    /**
     * Sets the current KeyStoreData for the pane.
     * If a non-null KeyStoreData is provided, the pane will be updated with the associated information.
     * If null, the pane will be reset to an empty state.
     *
     * @param keyStore the KeyStoreData to set for this pane; can be null to reset the content
     */
    private void setKeyStore(@Nullable KeyStoreData keyStore) {
        LOG.debug("setKeyStore({})", keyStore);
        this.keyStore = keyStore;
        updateContent();
        manager.contentChanged(this);
        if (keyStore != null) {
            ApplicationUtil.recentlyUsedDocuments().put(keyStore.path().toUri());
        }
    }

    /**
     * Updates the content of the KeyStorePane based on the current state of the keyStore field.
     * If the keyStore is null, it sets the content to an empty state using {@code setContentEmpty()}.
     * Otherwise, it updates the content using {@code setContent(KeyStoreData keyStore)}.
     */
    private void updateContent() {
        if (keyStore == null) {
            setContentEmpty();
        } else {
            setContent(keyStore);
        }
    }

    /**
     * Creates a new KeyStore by interacting with the user through a series of prompt dialogs.
     * <p>
     * This method allows the user to define the parameters of the new KeyStore, such as its
     * name, type, and password. It ensures the provided folder is valid, the password meets
     * certain strength criteria, and that the entered passwords match.
     * <p>
     * If a KeyStore with the specified name already exists in the chosen folder, an error is
     * shown to the user. Password strength is evaluated, and errors for weak passwords or
     * mismatched repeats are displayed.
     * <p>
     * Once the KeyStore is successfully created, it is saved to the specified location and
     * the application updates its state to reference the newly created KeyStore.
     * <p>
     * If any errors occur during the process, such as security-related issues or file output
     * problems, appropriate error messages are shown to the user in dialog alerts.
     * <p>
     * Dialog behaviors:
     * - Prompts for folder selection, name, type, password, and repeat password.
     * - Enforces validation rules for directory existence, non-empty input, password strength, and password match.
     * <p>
     * Error handling:
     * - Errors during KeyStore creation (e.g., invalid parameters or security exceptions)
     *   result in error dialogs specifying the issue.
     * - Errors related to saving the KeyStore to a file (e.g., file already existing or
     *   I/O issues) lead to file-specific error dialogs.
     */
    private void createKeyStore() {
        LOG.debug("createKeyStore()");

        KeyStoreDialogs.showCreateNewKeyStoreDialog(getScene().getWindow(), this::getInitialFolder)
                .ifPresent(ir -> ir.onResult(ButtonType.OK, map -> {
                                    try {
                                        Path folder = (Path) map.get(KeyStoreDialogs.ID_FOLDER);
                                        String keyName = (String) map.get(KeyStoreDialogs.ID_NAME);
                                        KeyStoreType type = (KeyStoreType) map.get(KeyStoreDialogs.ID_KEY_TYPE);
                                        String password = (String) map.get(KeyStoreDialogs.ID_PASSWORD);

                                        Path keyStorePath = folder.resolve(keyName + '.' + type.getExtension());

                                        if (Files.exists(keyStorePath)) {
                                            throw new IOException(I18N.format("dua3.keystoremanager.pane.error.file_already_exists", keyStorePath));
                                        }

                                        KeyStore instance = KeyStoreUtil.createKeyStore(type, password.toCharArray());
                                        KeyStoreUtil.saveKeyStoreToFile(instance, keyStorePath, password.toCharArray());

                                        setKeyStore(new KeyStoreData(instance, password, keyStorePath));

                                        LOG.debug("New KeyStore created at {}", keyStorePath);

                                        Dialogs.alert(getScene().getWindow(), Alert.AlertType.INFORMATION, MessageFormatter.i18n())
                                                .title("dua3.keystoremanager.pane.info.keystore_created.title")
                                                .header("dua3.keystoremanager.pane.info.keystore_created.header")
                                                .showAndWait();
                                    } catch (GeneralSecurityException e) {
                                        LOG.warn("Security error creating KeyStore", e);
                                        Dialogs.alert(getScene().getWindow(), Alert.AlertType.ERROR, MessageFormatter.i18n())
                                                .title("dua3.keystoremanager.pane.error.keystore_creation_failed.title")
                                                .header("dua3.keystoremanager.pane.error.keystore_creation_failed.header")
                                                .text(MessageFormatter.literal(e.getMessage()))
                                                .showAndWait();
                                    } catch (IOException e) {
                                        LOG.warn("I/O error creating KeyStore", e);
                                        Dialogs.alert(getScene().getWindow(), Alert.AlertType.ERROR, MessageFormatter.i18n())
                                                .title("dua3.keystoremanager.pane.error.keystore_creation_failed.title")
                                                .header("dua3.keystoremanager.pane.error.keystore_save_failed.header")
                                                .text(MessageFormatter.literal(e.getMessage()))
                                                .showAndWait();
                                    }
                                }
                        )
                );
    }

    /**
     * Determines the initial folder path by converting the last used URI to a Path object.
     * If the resulting Path is not a directory, it traverses up the directory hierarchy
     * until it finds a valid directory or reaches the top. If no valid directory is found,
     * it defaults to the user's home directory.
     *
     * @return The initial directory Path, either derived from the last used URI or the user's home directory.
     */
    private Path getInitialFolder() {
        Path path = IoUtil.toPath(ApplicationUtil.recentlyUsedDocuments().getLastUri());
        while (path != null && !Files.isDirectory(path)) {
            path = path.getParent();
        }
        return Objects.requireNonNullElse(path, IoUtil.getUserHome());
    }

    /**
     * Loads a KeyStore by prompting the user to select a file and enter its password through dialog windows.
     * <p>
     * This method begins by allowing the user to choose a KeyStore file using a file chooser dialog.
     * The selected file must match one of the predefined extension filters. If a valid file is chosen,
     * the user is prompted to enter the KeyStore password through a password dialog.
     * <p>
     * Password entry is retried if the input is invalid or if there are errors during the loading process.
     * Specifically:
     * - If the KeyStore fails to load due to an incorrect password or security issue, the user is alerted
     *   with an appropriate error message and has the option to retry entering the password.
     * - Input/output errors encountered while reading the KeyStore file are displayed in an error dialog.
     * <p>
     * Once the KeyStore is successfully loaded, it is set using the `setKeyStore` method, and the retry
     * process is terminated. If the operation is cancelled at any point, the retry loop is exited.
     * <p>
     * Dialog interactions in this method include:
     * - File chooser dialog for selecting KeyStore files.
     * - Password entry dialog with dynamic retry options in case of errors.
     * - Alert dialogs for displaying I/O or security-related errors.
     */
    private void loadKeyStore() {
        LOG.debug("loadKeyStore()");

        Dialogs.chooseFile(getScene().getWindow())
                .filter(EXTENSION_FILTERS)
                .selectedFilter(EXTENSION_FILTERS[0])
                .initialFile(IoUtil.toPath(ApplicationUtil.recentlyUsedDocuments().getLastUri()))
                .showOpenDialog()
                .ifPresent(this::loadKeyStoreFromFile);
    }

    private void loadKeyStoreFromFile(Path path) {
        AtomicBoolean retry = new AtomicBoolean(true);
        while (retry.get()) {
            Dialogs.prompt(getScene().getWindow(), MessageFormatter.i18n())
                    .mode(PromptMode.PASSWORD)
                    .title("dua3.keystoremanager.pane.keystore.password.title")
                    .header("dua3.keystoremanager.pane.keystore.password.enter.password", path.getFileName())
                    .showAndWait()
                    .ifPresentOrElse(password -> {
                                try {
                                    KeyStore newKeyStore = KeyStoreUtil.loadKeyStore(path, password.toCharArray());
                                    setKeyStore(new KeyStoreData(newKeyStore, password, path));
                                    retry.set(false);
                                    LOG.debug("KeyStore loaded from {}", path);
                                } catch (IOException e) {
                                    LOG.warn("I/O error loading KeyStore", e);
                                    Dialogs.alert(getScene().getWindow(), Alert.AlertType.ERROR, MessageFormatter.i18n())
                                            .title("dua3.keystoremanager.error.loading.keystore")
                                            .header("dua3.keystoremanager.keystore.load.io.error")
                                            .text(MessageFormatter.literal(e.getMessage()))
                                            .showAndWait();
                                } catch (GeneralSecurityException e) {
                                    LOG.warn("Security error loading KeyStore", e);
                                    Dialogs.alert(getScene().getWindow(), Alert.AlertType.ERROR, MessageFormatter.i18n())
                                            .title("dua3.keystoremanager.error.loading.keystore")
                                            .header("dua3.keystoremanager.keystore.load.security.error")
                                            .text(MessageFormatter.literal(e.getMessage()))
                                            .showAndWait();
                                }
                            },
                            () -> retry.set(false)
                    );
        }
    }

    private void importKeyStoreFromClipboard() {
        LOG.debug("importKeyStoreFromClipboard()");

        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasFiles()) {
            List<Path> files = clipboard.getFiles().stream().map(File::toPath).toList();
            if (!files.isEmpty()) {
                loadKeyStoreFromFile(files.getFirst());
                return;
            }
        }

        if (clipboard.hasString()) {
            String content = clipboard.getString();
            try {
                // remove PEM headers/footers and whitespace if present
                String base64 = content
                        .replaceFirst("-----BEGIN .*-----", "")
                        .replaceFirst("-----END .*-----", "")
                        .replaceAll("\\s", "");
                byte[] data = java.util.Base64.getDecoder().decode(base64);
                Dialogs.chooseFile(getScene().getWindow())
                        .filter(EXTENSION_FILTERS)
                        .selectedFilter(EXTENSION_FILTERS[0])
                        .initialFile(getInitialFolder().resolve("clipboard.p12"))
                        .showSaveDialog()
                        .ifPresent(path -> {
                            try {
                                Files.write(path, data);
                                loadKeyStoreFromFile(path);
                            } catch (IOException e) {
                                LOG.warn("I/O error saving KeyStore from clipboard", e);
                                Dialogs.alert(getScene().getWindow(), Alert.AlertType.ERROR, MessageFormatter.i18n())
                                        .title("dua3.keystoremanager.pane.error.saving_keystore")
                                        .header("dua3.keystoremanager.pane.error.saving_keystore.header")
                                        .text(MessageFormatter.literal(e.getMessage()))
                                        .showAndWait();
                            }
                        });
                return;
            } catch (IllegalArgumentException e) {
                LOG.warn("Clipboard content is not valid Base64", e);
            }
        }

        Dialogs.alert(getScene().getWindow(), Alert.AlertType.WARNING, MessageFormatter.i18n())
                .title("dua3.keystoremanager.pane.import_keystore_from_clipboard.title")
                .header("dua3.keystoremanager.pane.import_keystore_from_clipboard.invalid.header")
                .text("dua3.keystoremanager.pane.import_keystore_from_clipboard.invalid.text")
                .showAndWait();
    }

    /**
     * Sets the content of the KeyStorePane to indicate that no keystore is currently loaded.
     * The method updates the layout to display a placeholder message and relevant buttons
     * for creating or loading a keystore.
     * <p>
     * The displayed placeholder includes:
     * - A message indicating no keystore is available.
     * - A logo.
     * - Two buttons for user interaction: one to create a new keystore and another to load an existing keystore.
     * <p>
     * The layout dynamically adjusts its size by binding its properties to the pane's dimensions.
     * This ensures the UI is properly scaled to match the parent container.
     */
    private void setContentEmpty() {
        LOG.debug("setContentEmpty()");

        name.setValue(EMPTY_NAME);
        ImageView logo = new ImageView(LOGO.fxImage());

        double buttonWidth = logo.getImage().getWidth();

        VBox vBox = new VBox(
                logo,
                Controls.button()
                        .text(I18N.get("dua3.keystoremanager.pane.button.create_keystore"))
                        .prefWidth(buttonWidth)
                        .action(this::createKeyStore)
                        .build(),
                Controls.button()
                        .text(I18N.get("dua3.keystoremanager.pane.button.load_keystore"))
                        .prefWidth(buttonWidth)
                        .action(this::loadKeyStore)
                        .build(),
                Controls.button()
                        .text(I18N.get("dua3.keystoremanager.pane.button.import_keystore_from_clipboard"))
                        .prefWidth(buttonWidth)
                        .action(this::importKeyStoreFromClipboard)
                        .build()
        );
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(8);

        StackPane stackPane = new StackPane(vBox);

        stackPane.prefWidthProperty().bind(widthProperty());
        stackPane.prefHeightProperty().bind(heightProperty());

        minWidthProperty().bind(stackPane.minWidthProperty());
        minHeightProperty().bind(stackPane.minHeightProperty());

        getChildren().setAll(stackPane);
    }

    /**
     * Sets the content of the KeyStorePane based on the provided KeyStoreData.
     * Updates the displayed name to the file name of the keystore's path
     * and clears the current children of the pane.
     *
     * @param keyStore the KeyStoreData containing the keystore and related information
     */
    private void setContent(KeyStoreData keyStore) {
        LOG.debug("setContent({})", keyStore);

        name.setValue(keyStore.path().getFileName().toString());

        // create table of keystore entries
        TableView<EntryRow> entriesTable = createEntriesTable(keyStore);

        // create button bar
        ButtonBar buttonBar = new ButtonBar();
        buttonBar.getButtons().setAll(
                Controls.button().text(I18N.get("dua3.keystoremanager.pane.menu.add.secret_key")).action(this::newSecretKey).build(),
                Controls.button().text(I18N.get("dua3.keystoremanager.pane.menu.add.private_key")).action(this::newPrivateKey).build(),
                Controls.button().text(I18N.get("dua3.keystoremanager.pane.menu.export")).action(this::export).build(),
                Controls.button().text(I18N.get("dua3.keystoremanager.pane.menu.save")).action(this::saveKeyStore).build(),
                Controls.button().text(I18N.get("dua3.keystoremanager.pane.menu.save_as")).action(this::saveKeyStoreAs).build()
        );

        // create the content pane
        Pane content = new VBox(entriesTable, buttonBar);
        VBox.setVgrow(entriesTable, Priority.ALWAYS);

        // make content fill the whole area
        content.prefWidthProperty().bind(widthProperty());
        content.prefHeightProperty().bind(heightProperty());

        minWidthProperty().bind(content.minWidthProperty());
        minHeightProperty().bind(content.minHeightProperty());

        // set content
        getChildren().setAll(content);
    }

    private void export() {
        ExportDialogs.showExportDialog(getScene().getWindow(), keyStore);
    }

    /**
     * Creates a new certificate and adds it to the existing keystore. This method interacts with the user via input dialogs
     * to collect necessary information and configuration for the certificate details, such as parent certificate, key size,
     * algorithm, and validity period. It supports creation of both self-signed and signed certificates.
     */
    private void newPrivateKey() {
        LOG.debug("newPrivateKey()");

        assert keyStore != null;

        try {
            InputValidatorFactory vf = new InputValidatorFactory(MessageFormatter.standard());

            String aliasStandAlone = I18N.get("dua3.keystoremanager.dialog.new_private_key.field.parent.none");

            KeyStore ks = keyStore.keyStore();

            // show input dialog
            PrivateKeyDialogs.showNewPrivateKeyDialog(getScene().getWindow(), ks, aliasStandAlone, vf)
                    .ifPresent(ir -> ir.onResult(ButtonType.OK, map -> {
                                try {
                                    String alias = (String) LangUtil.getOrThrow(map, PrivateKeyDialogs.Fields.ALIAS.id);
                                    AsymmetricAlgorithm algorithm = (AsymmetricAlgorithm) LangUtil.getOrThrow(map, PrivateKeyDialogs.Fields.ALGORITHM.id);
                                    int keySize = (int) LangUtil.getOrThrow(map, PrivateKeyDialogs.Fields.KEY_SIZE.id);
                                    int validDays = ((Number) LangUtil.getOrThrow(map, PrivateKeyDialogs.Fields.VALID_DAYS.id)).intValue();
                                    boolean enableCA = (boolean) map.getOrDefault(PrivateKeyDialogs.Fields.ENABLE_CA.id, false);

                                    // Build the subject string in X.500 Distinguished Name format
                                    String subject = buildSubjectString(map);

                                    // Generate key pair with selected algorithm and key size
                                    KeyPair keyPair = KeyUtil.generateKeyPair(algorithm, keySize);

                                    // get the parent key and certificate chain
                                    X509Certificate[] certificate;
                                    switch (LangUtil.getOrThrow(map, PrivateKeyDialogs.ID_PARENT)) {
                                        case String s when !s.equals(aliasStandAlone) -> {
                                            PrivateKey parentKey = KeyStoreUtil.loadPrivateKey(ks, s, keyStore.password().toCharArray());
                                            Certificate[] parentChain = KeyStoreUtil.loadCertificateChain(ks, s);
                                            X509Certificate[] x509Parents = DataUtil.convert(parentChain, X509Certificate[].class);
                                            assert x509Parents.length == parentChain.length;
                                            certificate = CertificateUtil.createX509Certificate(
                                                    keyPair,
                                                    subject,
                                                    validDays,
                                                    enableCA,
                                                    parentKey,
                                                    x509Parents
                                            );
                                            assert certificate.length == parentChain.length + 1;
                                            LOG.debug("Created a new signed certificate: {}", alias);
                                        }
                                        default -> {
                                            certificate = CertificateUtil.createSelfSignedX509Certificate(
                                                    keyPair, subject, validDays, enableCA
                                            );
                                            LOG.debug("Created a new self-signed certificate: {}", alias);
                                        }
                                    }

                                    // Verify the complete certificate chain AFTER creation
                                    if (!verifyCertificateChain(certificate, alias)) {
                                        return;
                                    }

                                    // add key
                                    ks.setKeyEntry(alias, keyPair.getPrivate(), keyStore.password().toCharArray(), certificate);
                                    LOG.debug("Added key entry for alias: {}", alias);

                                    updateKeyStoreEntries(keyStore);

                                    // save the keystore
                                    KeyStoreUtil.saveKeyStoreToFile(keyStore.keyStore(), keyStore.path(), keyStore.password().toCharArray());

                                    // inform the user
                                    Dialogs.alert(getScene().getWindow(), Alert.AlertType.INFORMATION, MessageFormatter.i18n())
                                            .title("dua3.keystoremanager.pane.info.certificate_created.title")
                                            .header("dua3.keystoremanager.pane.info.certificate_created.header")
                                            .text("dua3.keystoremanager.pane.info.certificate_created.text", alias)
                                            .showAndWait();
                                } catch (GeneralSecurityException | IOException e) {
                                    throw LangUtil.wrapException(e);
                                }
                            })
                    );
        } catch (UncheckedIOException e) {
            LOG.warn("I/O error creating private key", e);
            Dialogs.alert(getScene().getWindow(), Alert.AlertType.ERROR, MessageFormatter.i18n())
                    .title("dua3.keystoremanager.pane.error.creating_private_key.title")
                    .header("dua3.keystoremanager.pane.error.creating_private_key.io_error.header")
                    .text(MessageFormatter.literal(e.getCause().getMessage()))
                    .showAndWait();
        } catch (WrappedException e) {
            LOG.warn(ERROR_CREATING_PRIVATE_KEY, e.getCause());
            Dialogs.alert(getScene().getWindow(), Alert.AlertType.ERROR, MessageFormatter.i18n())
                    .title("dua3.keystoremanager.pane.error.creating_private_key.title")
                    .header("dua3.keystoremanager.pane.error.creating_private_key.header")
                    .text(MessageFormatter.literal(e.getCause().getMessage()))
                    .showAndWait();
        } catch (RuntimeException e) {
            LOG.warn("RuntimeException creating private key", e.getCause());
            Dialogs.alert(getScene().getWindow(), Alert.AlertType.ERROR, MessageFormatter.i18n())
                    .title("dua3.keystoremanager.pane.error.creating_private_key.title")
                    .header("dua3.keystoremanager.pane.error.creating_private_key.header")
                    .text(MessageFormatter.literal(e.getMessage()))
                    .showAndWait();
        }
    }

    private void newSecretKey() {
        LOG.debug("newSecretKey()");

        assert keyStore != null;

        try {
            InputValidatorFactory vf = new InputValidatorFactory(MessageFormatter.standard());

            KeyStore ks = keyStore.keyStore();

            // show input dialog
            SecretKeyDialogs.showNewSecretKeyDialog(getScene().getWindow(), ks, vf)
                    .map(InputResult::data)
                    .ifPresent(map -> {
                        try {
                            String alias = (String) LangUtil.getOrThrow(map, SecretKeyDialogs.ID_ALIAS);
                            SymmetricAlgorithm algorithm = (SymmetricAlgorithm) LangUtil.getOrThrow(map, SecretKeyDialogs.ID_ALGORITHM);
                            SecretKey key = KeyUtil.generateSecretKey(algorithm.getDefaultKeySize(), algorithm);
                            KeyStoreUtil.storeSecretKey(ks, alias, key, keyStore.password().toCharArray());
                        } catch (GeneralSecurityException e) {
                            throw LangUtil.wrapException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            LOG.warn("I/O error creating public key", e);
            Dialogs.alert(getScene().getWindow(), Alert.AlertType.ERROR, MessageFormatter.i18n())
                    .title("dua3.keystoremanager.pane.error.creating_public_key.title")
                    .header("dua3.keystoremanager.pane.error.creating_public_key.io_error.header")
                    .text(MessageFormatter.literal(e.getCause().getMessage()))
                    .showAndWait();
        } catch (WrappedException e) {
            LOG.warn(ERROR_CREATING_PUBLIC_KEY, e.getCause());
            Dialogs.alert(getScene().getWindow(), Alert.AlertType.ERROR, MessageFormatter.i18n())
                    .title("dua3.keystoremanager.pane.error.creating_public_key.title")
                    .header("dua3.keystoremanager.pane.error.creating_public_key.header")
                    .text(MessageFormatter.literal(e.getCause().getMessage()))
                    .showAndWait();
        } catch (RuntimeException e) {
            LOG.warn("RuntimeException creating public key", e.getCause());
            Dialogs.alert(getScene().getWindow(), Alert.AlertType.ERROR, MessageFormatter.i18n())
                    .title("dua3.keystoremanager.pane.error.creating_public_key.title")
                    .header("dua3.keystoremanager.pane.error.creating_public_key.header")
                    .text(MessageFormatter.literal(e.getMessage()))
                    .showAndWait();
        }
    }

    /**
     * Builds a subject string from the provided map by concatenating specific certificate fields
     * if they are present in the map. Each field is added in the format "key=value", separated by commas.
     *
     * @param map the map containing certificate fields as keys and their corresponding values
     * @return the constructed subject string containing the specified certificate field values
     */
    private static String buildSubjectString(Map<String, Object> map) {
        StringBuilder subjectBuilder = new StringBuilder();
        Stream.of(
                PrivateKeyDialogs.Fields.COMMON_NAME,
                PrivateKeyDialogs.Fields.ORGANIZATION,
                PrivateKeyDialogs.Fields.ORGANIZATIONAL_UNIT,
                PrivateKeyDialogs.Fields.COUNTRY,
                PrivateKeyDialogs.Fields.STATE,
                PrivateKeyDialogs.Fields.LOCALITY,
                PrivateKeyDialogs.Fields.EMAIL
        ).forEach(cf -> LangUtil.ifPresent(map, cf.id, v -> {
            if (!subjectBuilder.isEmpty()) {
                subjectBuilder.append(", ");
            }
            subjectBuilder.append(cf.id).append("=").append(v);
        }));

        return subjectBuilder.toString();
    }

    /**
     * Verifies the provided certificate chain and ensures its validity.
     *
     * @param certificate the array of X509Certificate objects representing the certificate chain to be verified
     * @param alias the alias associated with the certificate chain, used for logging and error reporting
     * @return true if the certificate chain is successfully verified, false otherwise
     */
    private boolean verifyCertificateChain(X509Certificate[] certificate, String alias) {
        try {
            CertificateUtil.verifyCertificateChain(certificate);
            LOG.debug("Certificate chain verified successfully for alias: {}", alias);
        } catch (CertificateException e) {
            LOG.warn("Certificate chain verification failed for key alias: {}", alias, e);
            Dialogs.alert(getScene().getWindow(), Alert.AlertType.ERROR, MessageFormatter.i18n())
                    .title("dua3.keystoremanager.pane.error.certificate_verification.title")
                    .header("dua3.keystoremanager.pane.error.certificate_verification.header", alias)
                    .text(MessageFormatter.literal(e.getMessage()))
                    .showAndWait();
            return false;
        }
        return true;
    }

    private record EntryRow(String alias, KeyStoreEntryType type, Instant created) {}

    private TableView<EntryRow> createEntriesTable(KeyStoreData keyStore) {
        LOG.debug("createEntriesTable({})", keyStore);

        TableView<EntryRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        TableColumn<EntryRow, String> colAlias = new TableColumn<>(I18N.get("dua3.keystoremanager.pane.table.column.alias"));
        colAlias.setCellValueFactory(cd -> new SimpleStringProperty((cd.getValue()).alias));
        colAlias.setPrefWidth(250);

        TableColumn<EntryRow, String> colType = new TableColumn<>(I18N.get("dua3.keystoremanager.pane.table.column.type"));
        colType.setCellValueFactory(cd -> new SimpleStringProperty(
                getEntryDisplayType(keyStore, cd.getValue().alias, cd.getValue().type)
        ));
        colType.setPrefWidth(150);

        TableColumn<EntryRow, Instant> colCreated = new TableColumn<>(I18N.get("dua3.keystoremanager.pane.table.column.created"));
        colCreated.setCellValueFactory(cd -> new SimpleObjectProperty<>((cd.getValue()).created));
        colCreated.setPrefWidth(200);

        table.getColumns().setAll(colAlias, colType, colCreated);

        // allow multiple row selection
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        updateKeyStoreEntries(keyStore);

        table.setItems(keyStoreItems);

        // Open details dialog on double-click
        table.setRowFactory(tv -> {
            TableRow<EntryRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    EntryRow item = row.getItem();
                    KeyStoreDialogs.showDetails(getScene().getWindow(), keyStore, item.alias);
                }
            });
            return row;
        });

        return table;
    }

    private void updateKeyStoreEntries(KeyStoreData keyStore) {
        keyStoreItems.setAll(getKeyStoreItems(keyStore));
    }

    private static List<EntryRow> getKeyStoreItems(KeyStoreData keyStore) {
        List<EntryRow> items = new ArrayList<>();
        try {
            KeyStore ks = keyStore.keyStore();
            var aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                KeyStoreEntryType type = getKeyStoreEntryType(keyStore, alias);
                Instant created = getCreationDate(keyStore, alias);

                items.add(new EntryRow(alias, type, created));
            }
        } catch (Exception e) {
            LOG.warn("Error loading keystore entries", e);
            // if anything goes wrong, leave the table empty; UI will still render
        }
        return items;
    }

    /**
     * Retrieves the creation date of a specific entry in the given keystore, identified by its alias.
     * If the creation date cannot be determined or an error occurs, null is returned.
     *
     * @param keyStore the KeyStoreData object containing the keystore
     * @param alias the alias of the keystore entry whose creation date is to be retrieved
     * @return the creation date of the specified keystore entry as an Instant, or null if the date cannot be determined
     */
    private static @Nullable Instant getCreationDate(KeyStoreData keyStore, String alias) {
        Instant created;
        try {
            var date = keyStore.keyStore().getCreationDate(alias);
            created = (date == null) ? null : date.toInstant();
        } catch (Exception e) {
            LOG.warn("Error loading keystore entry", e);
            created = null;
        }
        return created;
    }

    /**
     * Determines the type of the specified key store entry based on the provided alias.
     *
     * @param keyStore the key store containing the entries
     * @param alias the alias of the entry to identify
     * @return the type of the key store entry, either PRIVATE_KEY, SECRET_KEY, CERTIFICATE,
     *         or UNKNOWN if the type cannot be determined or an error occurs
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

    /**
     * Build a display string for the entry type including the key algorithm where possible,
     * e.g. "RSA Private Key", "EC Public Key", "AES Secret Key".
     */
    private static String getEntryDisplayType(KeyStoreData keyStore, String alias, KeyStoreEntryType baseType) {
        try {
            KeyStore ks = keyStore.keyStore();
            switch (baseType) {
                case PRIVATE_KEY -> {
                    var entry = ks.getEntry(alias, new KeyStore.PasswordProtection(keyStore.password().toCharArray()));
                    if (entry instanceof KeyStore.PrivateKeyEntry pke && pke.getPrivateKey() != null) {
                        String alg = pke.getPrivateKey().getAlgorithm();
                        if (alg != null && !alg.isBlank()) {
                            return I18N.format("dua3.keystoremanager.pane.type.format.private_key", alg);
                        }
                    }
                    return I18N.get("dua3.keystoremanager.pane.type.private_key");
                }
                case SECRET_KEY -> {
                    var entry = ks.getEntry(alias, new KeyStore.PasswordProtection(keyStore.password().toCharArray()));
                    if (entry instanceof KeyStore.SecretKeyEntry ske && ske.getSecretKey() != null) {
                        String alg = ske.getSecretKey().getAlgorithm();
                        if (alg != null && !alg.isBlank()) {
                            return I18N.format("dua3.keystoremanager.pane.type.format.secret_key", alg);
                        }
                    }
                    return I18N.get("dua3.keystoremanager.pane.type.secret_key");
                }
                case CERTIFICTE -> {
                    Certificate cert = ks.getCertificate(alias);
                    if (cert instanceof X509Certificate x509 && x509.getPublicKey() != null) {
                        String alg = x509.getPublicKey().getAlgorithm();
                        if (alg != null && !alg.isBlank()) {
                            return I18N.format("dua3.keystoremanager.pane.type.format.public_key", alg);
                        }
                    }
                    return I18N.get("dua3.keystoremanager.pane.type.public_key");
                }
                case KEY -> {
                    return I18N.get("dua3.keystoremanager.pane.type.key");
                }
                case UNKNOWN -> {
                    return I18N.get("dua3.keystoremanager.pane.type.unknown");
                }
            }
        } catch (GeneralSecurityException e) {
            LOG.warn("Unable to determine algorithm for entry '{}': {}", alias, e.getMessage());
        }
        // Fallback to enum text
        return baseType.toString();
    }

    /**
     * Saves the current KeyStore data to a file and provides user feedback through dialogs.
     * <p>
     * This method attempts to save the KeyStore associated with the current state to the file
     * specified in the KeyStore's path. The password for the KeyStore is retrieved to facilitate
     * the save operation. Upon successful save, an informational dialog is displayed, indicating
     * that the KeyStore has been successfully saved. In case of errors during the save process,
     * an error dialog is displayed with the corresponding error message.
     * <p>
     * Exception Handling:
     * - If a GeneralSecurityException occurs during the save process, an error dialog is displayed
     *   detailing the issue.
     * - If an IOException occurs during file output, an error dialog is displayed with the error details.
     * <p>
     * Dialog Interactions:
     * - An informational dialog is shown upon successful save, confirming the operation to the user.
     * - An error dialog is displayed in case of any exceptions, providing relevant error messages.
     */
    private void saveKeyStore() {
        LOG.debug("saveKeyStore()");

        try {
            KeyStoreUtil.saveKeyStoreToFile(keyStore.keyStore(), keyStore.path(), keyStore.password().toCharArray());
            LOG.debug("KeyStore saved to {}", keyStore.path());
            Dialogs.alert(getScene().getWindow(), Alert.AlertType.INFORMATION, MessageFormatter.i18n())
                    .title("dua3.keystoremanager.pane.info.keystore_saved.title")
                    .header("dua3.keystoremanager.pane.info.keystore_saved.header")
                    .showAndWait();
        } catch (GeneralSecurityException | IOException e) {
            LOG.warn("Error saving keystore to {}", keyStore.path(), e);
            Dialogs.alert(getScene().getWindow(), Alert.AlertType.ERROR, MessageFormatter.i18n())
                    .title("dua3.keystoremanager.pane.error.saving_keystore")
                    .header("dua3.keystoremanager.pane.error.saving_keystore.header")
                    .text(MessageFormatter.literal(e.getMessage()))
                    .showAndWait();
        }
    }

    /**
     * Opens a save file dialog, allowing the user to save the current KeyStore to a chosen file path.
     * <p>
     * This method ensures the saved file adheres to the correct file extension based on the KeyStore's current path.
     * If a valid save path is selected, the method attempts to save the KeyStore to the specified location. Upon
     * successful save, an informational dialog is shown to the user, confirming the save operation. The KeyStore
     * data is then updated with the newly specified file path.
     * <p>
     * In case of errors (e.g., security exceptions, file I/O issues), an error dialog is displayed that informs
     * the user of the failure and provides the corresponding error message.
     * <p>
     * The save operation involves the following steps:
     * - Displays a save file dialog with a file filter matching the KeyStore's current file extension.
     * - If the user selects a valid file path, attempts to save the KeyStore to the new location.
     * - If the save is successful, updates the application's state with the new file path and displays a
     *   confirmation dialog.
     * - If an error occurs during saving, displays an appropriate error message to the user in a dialog.
     */
    private void saveKeyStoreAs() {
        LOG.debug("saveKeyStoreAs()");

        assert keyStore != null;

        String extension = IoUtil.getExtension(keyStore.path());
        Dialogs.chooseFile(getScene().getWindow())
                .addFilter("*." + extension, Pattern.quote(extension))
                .showSaveDialog()
                .ifPresent(path -> {
                    try {
                        KeyStoreData newKeyStore = new KeyStoreData(keyStore.keyStore(), keyStore.password(), path);
                        KeyStoreUtil.saveKeyStoreToFile(newKeyStore.keyStore(), newKeyStore.path(), newKeyStore.password().toCharArray());
                        LOG.debug("KeyStore saved as {}", newKeyStore.path());
                        Dialogs.alert(getScene().getWindow(), Alert.AlertType.INFORMATION, MessageFormatter.i18n())
                                .title("dua3.keystoremanager.pane.info.keystore_saved.title")
                                .header("dua3.keystoremanager.pane.info.keystore_saved_as.header", newKeyStore.path().getFileName())
                                .text("dua3.keystoremanager.pane.info.keystore_saved_as.text")
                                .showAndWait();
                        setKeyStore(newKeyStore);
                    } catch (GeneralSecurityException | IOException e) {
                        LOG.warn("Error saving keystore to {}", path, e);
                        Dialogs.alert(getScene().getWindow(), Alert.AlertType.ERROR, MessageFormatter.i18n())
                                .title("dua3.keystoremanager.pane.error.saving_keystore")
                                .header("dua3.keystoremanager.pane.error.saving_keystore.header")
                                .text(MessageFormatter.literal(e.getMessage()))
                                .showAndWait();
                    }
                });
    }

    /**
     * Retrieves the key store data if available.
     *
     * @return an {@code Optional} containing the {@code KeyStoreData} if present, or an empty {@code Optional} otherwise.
     */
    public Optional<KeyStoreData> getKeyStore() {
        return Optional.ofNullable(keyStore);
    }
}
