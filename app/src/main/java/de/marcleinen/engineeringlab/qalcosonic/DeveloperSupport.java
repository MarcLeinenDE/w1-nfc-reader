package de.marcleinen.engineeringlab.qalcosonic;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Shared voluntary-support link. No in-app payment handling or entitlement logic lives here. */
final class DeveloperSupport {
    static final String PAYPAL_URL = "https://www.paypal.me/ccaa/";

    private DeveloperSupport() { }

    static Intent browserIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(PAYPAL_URL));
    }

    static boolean openExternal(Context context) {
        if (context == null) return false;
        try {
            Intent intent = browserIntent();
            if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }
}
