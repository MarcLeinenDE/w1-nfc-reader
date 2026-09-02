package de.marcleinen.engineeringlab.qalcosonic;

import android.app.Activity;
import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import androidx.appcompat.app.AppCompatDelegate;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** UI-only preferences. No NFC or meter protocol behavior lives here. */
final class UiPreferences {
    static final String LANGUAGE_SYSTEM = "system";
    static final String THEME_SYSTEM = "system";
    static final String THEME_LIGHT = "light";
    static final String THEME_DARK = "dark";

    private static final String PREFS = "ui_preferences";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_THEME = "theme";
    private static final String KEY_PLATFORM_SYNCED_LANGUAGE = "platform_synced_language";

    private UiPreferences() {}

    static Context wrapContext(Context base) {
        SharedPreferences prefs = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        boolean changed = false;

        // Theme selection is intentionally handled by AppCompatDelegate. Applying uiMode here as
        // well causes Theme.Material3.DayNight to re-apply the process default and can leave a
        // recreated Activity in the old mode on real devices.
        String language = getEffectiveLanguageTag(base, prefs);
        if (language != null && !LANGUAGE_SYSTEM.equals(language)) {
            Locale locale = Locale.forLanguageTag(language);
            config.setLocale(locale);
            Locale.setDefault(locale);
            changed = true;
        }

        return changed ? base.createConfigurationContext(config) : base;
    }

    /**
     * Android 13+ owns an additional per-app locale state. The sync marker lets a restored backup
     * update that state once while still respecting later changes made in Android system settings.
     */
    private static String getEffectiveLanguageTag(Context context, SharedPreferences prefs) {
        String stored = prefs.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM);
        if (stored == null) stored = LANGUAGE_SYSTEM;
        if (Build.VERSION.SDK_INT >= 33) {
            boolean hasSyncMarker = prefs.contains(KEY_PLATFORM_SYNCED_LANGUAGE);
            String synced = prefs.getString(KEY_PLATFORM_SYNCED_LANGUAGE, LANGUAGE_SYSTEM);
            if (!hasSyncMarker || !stored.equals(synced)) {
                String normalized = normalizeLanguage(context, stored);
                applyPlatformLocale(context, normalized);
                prefs.edit()
                        .putString(KEY_LANGUAGE, normalized)
                        .putString(KEY_PLATFORM_SYNCED_LANGUAGE, normalized)
                        .apply();
                return normalized;
            }

            LocaleManager manager = context.getSystemService(LocaleManager.class);
            if (manager != null) {
                LocaleList locales = manager.getApplicationLocales();
                if (!locales.isEmpty()) return locales.get(0).toLanguageTag();
                // A cleared platform list means the user chose the system language in Android.
                return LANGUAGE_SYSTEM;
            }
        }
        return stored;
    }

    static String getTheme(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME, THEME_SYSTEM);
    }

    static int appCompatNightMode(Context context) {
        return appCompatNightModeForTheme(getTheme(context));
    }

    static int appCompatNightModeForTheme(String theme) {
        if (THEME_LIGHT.equals(theme)) return AppCompatDelegate.MODE_NIGHT_NO;
        if (THEME_DARK.equals(theme)) return AppCompatDelegate.MODE_NIGHT_YES;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    static void applyThemeMode(Context context) {
        int desired = appCompatNightMode(context);
        if (AppCompatDelegate.getDefaultNightMode() != desired) {
            AppCompatDelegate.setDefaultNightMode(desired);
        }
    }

    static void setTheme(Activity activity, String theme) {
        String normalized = normalizeTheme(theme);
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME, normalized)
                .commit();
        int desired = appCompatNightModeForTheme(normalized);
        int previous = AppCompatDelegate.getDefaultNightMode();
        AppCompatDelegate.setDefaultNightMode(desired);
        // AppCompat normally recreates active Activities when the mode changes. If the global mode
        // already matched (for example after a process restore), explicitly recreate this screen so
        // the newly persisted selection still becomes visible immediately.
        if (previous == desired) {
            activity.getWindow().getDecorView().post(activity::recreate);
        }
    }

    static String getSelectedLanguageTag(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return getEffectiveLanguageTag(context, prefs);
    }

    static void setLanguage(Activity activity, String languageTag) {
        String normalized = normalizeLanguage(activity, languageTag);
        persistLanguage(activity, normalized);

        // Always recreate explicitly. The preference-backed configuration wrapper makes the
        // selected language available to the new Activity immediately, including on Android 13+.
        activity.getWindow().getDecorView().post(activity::recreate);
    }

    /** Restores portable settings, including Android 13+'s system-owned per-app LocaleManager state. */
    static void restorePortable(Context context, String theme, String languageTag) {
        String normalizedTheme = normalizeTheme(theme);
        String normalizedLanguage = normalizeLanguage(context, languageTag);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_THEME, normalizedTheme)
                .putString(KEY_LANGUAGE, normalizedLanguage)
                .putString(KEY_PLATFORM_SYNCED_LANGUAGE, normalizedLanguage)
                .commit();
        applyPlatformLocale(context, normalizedLanguage);
        applyThemeMode(context);
    }

    private static void persistLanguage(Context context, String languageTag) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, languageTag)
                .putString(KEY_PLATFORM_SYNCED_LANGUAGE, languageTag)
                .apply();
        applyPlatformLocale(context, languageTag);
    }

    private static void applyPlatformLocale(Context context, String languageTag) {
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            if (manager != null) {
                manager.setApplicationLocales(
                        LANGUAGE_SYSTEM.equals(languageTag)
                                ? LocaleList.getEmptyLocaleList()
                                : LocaleList.forLanguageTags(languageTag));
            }
        }
    }

    private static String normalizeTheme(String theme) {
        if (THEME_LIGHT.equals(theme) || THEME_DARK.equals(theme)) return theme;
        return THEME_SYSTEM;
    }

    private static String normalizeLanguage(Context context, String languageTag) {
        if (languageTag == null || LANGUAGE_SYSTEM.equals(languageTag)) return LANGUAGE_SYSTEM;
        for (String supported : getSupportedLocaleTags(context)) {
            if (supported.equalsIgnoreCase(languageTag)) return supported;
        }
        return LANGUAGE_SYSTEM;
    }

    static List<String> getSupportedLocaleTags(Context context) {
        Set<String> tags = new LinkedHashSet<>();
        try {
            XmlPullParser parser = context.getResources().getXml(R.xml.locales_config);
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "locale".equals(parser.getName())) {
                    String tag = parser.getAttributeValue(
                            "http://schemas.android.com/apk/res/android",
                            "name");
                    if (tag == null) {
                        tag = parser.getAttributeValue(null, "name");
                    }
                    if (tag != null && !tag.trim().isEmpty()) {
                        tags.add(tag.trim());
                    }
                }
            }
        } catch (Exception ignored) {
            // Keep a useful fallback if a future translation PR breaks locale metadata.
        }

        if (tags.isEmpty()) {
            tags.add("en");
            tags.add("de");
        }
        return new ArrayList<>(tags);
    }
}
