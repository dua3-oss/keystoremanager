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

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import com.dua3.app.keystoremanager.dialogs.PemDialogs;
import com.dua3.utility.i18n.I18N;
import com.dua3.utility.application.ApplicationUtil;
import com.dua3.utility.application.UiMode;
import com.dua3.utility.fx.FxUtil;
import com.dua3.utility.fx.PlatformHelper;
import com.dua3.utility.fx.controls.Controls;
import com.dua3.utility.fx.controls.Dialogs;
import com.dua3.utility.lang.LangUtil;
import com.dua3.utility.lang.Platform;
import com.dua3.utility.math.MathUtil;
import com.dua3.utility.text.MessageFormatter;
import javafx.application.Application;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

/**
 * KeyStoreManager is a JavaFX application for managing cryptographic keystores.
 * It provides a user interface to handle multiple keystores via a tabbed layout.
 * <p>
 * This class extends the Application class and serves as the main entry point
 * for the JavaFX application. It initializes the user interface with
 * customizable theming and dynamically manages tabs for each loaded keystore.
 * <p>
 * - The application uses the PrimerDark stylesheet for a consistent UI theme.
 * - Dynamically controls the visibility and management of the tab headers,
 *   depending on the number of tabs present in the application.
 * - Provides a mechanism to add new tabs displaying keystore data
 *   using the {@code addKeyStoreTab} method.
 */
public class KeyStoreManager extends Application {
    private static final Logger LOG = LogManager.getLogger(KeyStoreManager.class);
    private static final I18N I18N = com.dua3.utility.i18n.I18N.getInstance();

    static {
        ApplicationUtil.initApplicationPreferences(Preferences.userNodeForPackage(KeyStoreManager.class));
        ApplicationUtil.addDarkModeListener(KeyStoreManager::setDarkMode);
        ApplicationUtil.setUiMode(UiMode.SYSTEM_DEFAULT);
    }

    private final Property<UiMode> uiModeProperty = new SimpleObjectProperty<>(ApplicationUtil.getUiMode());

    public KeyStoreManager() {
        FxUtil.bindBidirectional(uiModeProperty, ApplicationUtil::getUiMode, KeyStoreManager::setUiMode, ApplicationUtil::addUiModeListener);
    }

    private static void setUiMode(UiMode mode) {
        ApplicationUtil.setUiMode(mode);
    }

    private static void setDarkMode(boolean enabled) {
        LOG.info("Setting dark mode to {}", enabled ? "enabled" : "disabled");
        Supplier<Theme> themeSupplier = enabled ? PrimerDark::new : PrimerLight::new;
        PlatformHelper.runAndWait(() -> {
            setUserAgentStylesheet(themeSupplier.get().getUserAgentStylesheet());
            LOG.debug("theme set to {}", enabled ? "dark" : "light");
        });
    }

    private final TabPane tabPane = new TabPane();

    @Override
    public void start(Stage primaryStage) {
        LOG.info("Starting KeyStoreManager...");

        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            LOG.error("Uncaught exception on thread {}", thread.getName(), throwable);
            Dialogs.alert(primaryStage, Alert.AlertType.ERROR, MessageFormatter.i18n())
                    .title("dua3.keystoremanager.manager.error.title")
                    .header("dua3.keystoremanager.manager.error.header")
                    .text("{}", LangUtil.formatThrowable(throwable))
                    .selectableText(true)
                    .resizable(true)
                    .showAndWait();
        });

        // open the initially empty tab
        addKeyStoreTab(null);

        int width = 1000;

        // Create a simple menu bar with a single 'File' menu (no items for now)
        MenuBar menuBar = new MenuBar(
                new Menu(I18N.get("dua3.keystoremanager.manager.menu.view"), null,
                        Controls.choiceMenu(List.of(UiMode.values()))
                                .text(I18N.get("dua3.keystoremanager.manager.menu.view.appearance"))
                                .bind(uiModeProperty)
                                .build()
                ),
                new Menu(I18N.get("dua3.keystoremanager.manager.menu.tools"), null,
                        Controls.menuItem().text(I18N.get("dua3.keystoremanager.manager.menu.tools.validate_pem")).action(this::verifyPem).build()
                )
        );
        if (Platform.isMacOS()) {
            menuBar.setUseSystemMenuBar(true);
            menuBar.setManaged(false);
        }

        // Use a BorderPane so the MenuBar can sit at the top and the TabPane in the center
        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(tabPane);

        Scene scene = new Scene(root, width, Math.round(width / MathUtil.GOLDEN_RATIO));

        primaryStage.setScene(scene);
        primaryStage.setTitle(I18N.get("dua3.keystoremanager.manager.title"));
        primaryStage.show();

        LOG.info("KeyStoreManager started.");
    }

    /**
     * Adds a new tab to the specified TabPane, displaying the provided KeyStoreData.
     * The tab's content is a KeyStorePane instance, which dynamically resizes
     * based on the dimensions of the TabPane.
     *
     * @param keyStore the KeyStoreData object containing the keystore information to display in the tab
     */
    void addKeyStoreTab(@Nullable KeyStoreData keyStore) {
        LOG.debug("addKeyStoreTab({}, {})", tabPane, keyStore);

        KeyStorePane content = new KeyStorePane(this, keyStore);
        Tab tab = new Tab(content.nameProperty().get(), content);
        tab.textProperty().bind(content.nameProperty());
        tab.setClosable(keyStore != null);
        content.prefWidthProperty().bind(tabPane.widthProperty());
        content.prefHeightProperty().bind(tabPane.heightProperty());

        tabPane.getTabs().add(tab);

        // select the newly created tab
        if (keyStore != null || tabPane.getTabs().size() == 1) {
            tabPane.getSelectionModel().select(tab);
        }
    }

    void verifyPem() {
        PemDialogs.showVerifyPemtKeyDialog(tabPane.getScene().getWindow());
    }

    /**
     * Handles changes in the content of a specified KeyStorePane instance
     * and ensures the state of tabs in the TabPane adheres to the defined
     * conditions. If the KeyStorePane contains keystore data and the last
     * tab also contains keystore data, a new empty tab is added.
     * <p>
     * If assertions are enabled, additional pre- and postconditions are
     * validated to ensure a consistent state.
     *
     * @param keyStorePane the KeyStorePane whose content has changed
     */
    public void contentChanged(KeyStorePane keyStorePane) {
        ObservableList<Tab> tabs = tabPane.getTabs();

        LangUtil.checkArg(
                keyStorePane.getKeyStore().isPresent() || tabs.isEmpty() || tabs.getLast().getContent() == keyStorePane,
                I18N.get("dua3.keystoremanager.manager.error.content_changed_precondition")
        );

        // assert preconditions
        assert !tabs.isEmpty() : "There must be at least one tab present in the tab pane when a tab reports changes";
        assert tabs.getLast().getContent() instanceof KeyStorePane : "Last tab should always be a KeyStorePane";

        // add empty tab if needed
        if (keyStorePane.getKeyStore().isPresent() && (((KeyStorePane) tabs.getLast().getContent()).getKeyStore().isPresent())) {
            addKeyStoreTab(null);
        }

        // make the tab closeable if it is not the empty tab
        Tab last = tabs.getLast();
        tabs.forEach(t -> t.setClosable(t != last));

        // assert post conditions
        assert tabs.stream().filter(t -> ((KeyStorePane) t.getContent()).getKeyStore().isEmpty()).count() == 1 : "There should be exactly one empty tab";
        assert ((KeyStorePane) tabs.getLast().getContent()).getKeyStore().isEmpty() : "Last tab should always be empty";
    }
}
