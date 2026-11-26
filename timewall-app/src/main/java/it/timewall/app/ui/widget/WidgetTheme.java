package it.timewall.app.ui.widget;

import android.content.SharedPreferences;
import android.graphics.Color;

import it.timewall.app.configuration.DashboardTheme;
import it.timewall.app.configuration.Palette;
import it.timewall.app.ui.common.ViewFactory;
import it.timewall.app.ui.settings.CustomThemeEditor;

/** Resolves the dashboard palette for widget content and configuration. */
final class WidgetTheme {
    final Palette palette;
    final boolean dark;
    final boolean legacy;
    final boolean custom;

    WidgetTheme(SharedPreferences settings) {
        DashboardTheme fallback = DashboardTheme.SLATE_AZURE_DARK;
        DashboardTheme theme = DashboardTheme.fromSavedId(settings.getInt("theme", fallback.id));
        int[] colors =
                theme == DashboardTheme.CUSTOM ? CustomThemeEditor.loadColors(settings) : null;
        if (colors == null && theme == DashboardTheme.CUSTOM) theme = fallback;
        palette = colors == null ? theme.palette : CustomThemeEditor.customPalette(colors);
        dark =
                colors == null
                        ? theme.dark
                        : ViewFactory.contrastingText(palette.background) == Color.WHITE;
        custom = colors != null;
        legacy = colors != null && colors.length == CustomThemeEditor.LEGACY_COLOR_COUNT;
    }
}
