package com.dua3.app.keystoremanager;

import com.dua3.utility.i18n.I18N;
import com.dua3.utility.i18n.I18NProvider;

import java.util.Locale;

public class ApplicationI18NProvider implements I18NProvider {
    @Override
    public I18N i18n() {
        return I18N.create("dua3.keystoremanager", Locale.getDefault());
    }
}
