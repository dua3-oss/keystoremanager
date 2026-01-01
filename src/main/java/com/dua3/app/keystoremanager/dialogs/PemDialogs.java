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

import com.dua3.utility.crypt.PemData;
import com.dua3.utility.i18n.I18N;
import com.dua3.utility.fx.controls.ButtonDef;
import com.dua3.utility.fx.controls.Dialogs;
import com.dua3.utility.fx.controls.InputDialogPane;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Formatter;
import java.util.Map;
import java.util.Objects;

public class PemDialogs {
    private static final Logger LOG = LogManager.getLogger(PemDialogs.class);
    private static final I18N I18N = com.dua3.utility.i18n.I18N.getInstance();

    public static void showVerifyPemtKeyDialog(Window owner) {
        TextField validationResult = new TextField("?");
        validationResult.setEditable(false);

        Dialogs.input(owner)
                .title(I18N.get("dua3.keystoremanager.dialog.pem.title"))
                .section(1, I18N.get("dua3.keystoremanager.dialog.pem.section.pem_data"))
                .inputText("pem", I18N.get("dua3.keystoremanager.dialog.pem.content"), () -> "")
                .inputPassword("passphrase", I18N.get("dua3.keystoremanager.dialog.pem.passphrase"), () -> "")
                .section(1, I18N.get("dua3.keystoremanager.dialog.pem.section.validation_results"))
                .node("validationResult", validationResult)
                .buttons(
                        ButtonDef.of(
                                ButtonType.CLOSE,
                                InputDialogPane::closeDialog
                        ),
                        ButtonDef.of(
                                ButtonType.APPLY,
                                (btn, result) -> false,
                                idp -> {
                                    Map<String, Object> result = (Map<String, Object>) idp.get();
                                    String pem = Objects.toString(result.get("pem"), "");
                                    String passphrase = Objects.toString(result.get("passphrase"), "");
                                    validationResult.setText(validatePem(pem, passphrase));
                                },
                                InputDialogPane::validProperty
                        )
                )
                .showAndWait();
    }

    private static String validatePem(String pem, String passphrase) {
        try (Formatter fmt = new Formatter()) {
            PemData pemData = PemData.parse(pem);
            for (var item : pemData) {
                fmt.format("%s%n", item);
            }
            return fmt.toString();
        } catch (PemData.PemException e) {
            LOG.warn("Exception while validating PEM", e);
            return I18N.format("dua3.keystoremanager.dialog.pem.error.parse_failed", e.getMessage());
        }
    }
}
