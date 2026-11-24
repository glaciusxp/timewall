package it.timewall.app.ui.info;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.text.Spannable;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import it.timewall.app.configuration.Palette;
import it.timewall.app.localization.I18n;
import it.timewall.app.localization.PrivacyNotice;
import it.timewall.app.ui.common.DashboardStyle;
import it.timewall.app.ui.common.ViewFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Read-only full-screen application information, offline privacy notice and third-party credits.
 */
public final class InfoScreen {
    private final Activity activity;
    private final DashboardStyle style;
    private final ViewFactory views;
    private final Runnable close;

    /**
     * Connects the current presentation and the host's common exit action without changing
     * preferences.
     */
    public InfoScreen(Activity activity, DashboardStyle style, Runnable close) {
        this.activity = activity;
        this.style = style;
        this.close = close;
        views = new ViewFactory(activity, style.palette, style.font, style.icons, style.dark);
    }

    /**
     * Builds the same header/sidebar proportions as Settings; each section scrolls independently.
     */
    public View createView() {
        Palette palette = style.palette;
        LinearLayout screen = views.column();
        android.util.TypedValue background = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.colorBackground, background, true);
        screen.setBackgroundColor(background.data);
        screen.setClickable(true);
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(views.dp(8), 0, views.dp(8), 0);
        int color = palette.accent | 0xff000000;
        int foreground = ViewFactory.contrastingText(color);
        header.setBackgroundColor(color);
        Button back =
                views.addHeaderButton(
                        header, "\uf053", I18n.text(activity, "back"), v -> close.run());
        back.setBackground(null);
        back.setTextColor(foreground);
        TextView title = views.label(I18n.text(activity, "about"), 24, true);
        title.setTextColor(foreground);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        View balance = new View(activity);
        header.addView(balance, new LinearLayout.LayoutParams(views.dp(48), 1));
        screen.addView(header, new LinearLayout.LayoutParams(-1, views.dp(64)));
        LinearLayout body = new LinearLayout(activity);
        screen.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView navigationScroll = new ScrollView(activity);
        LinearLayout navigation = views.column();
        navigationScroll.addView(navigation);
        body.addView(navigationScroll, new LinearLayout.LayoutParams(views.dp(330), -1));
        View divider = new View(activity);
        divider.setBackgroundColor(palette.border);
        body.addView(divider, new LinearLayout.LayoutParams(views.dp(1), -1));
        FrameLayout content = new FrameLayout(activity);
        body.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        String[] keys = {"timewall_info", "privacy", "licenses_credits"};
        String[] icons = {"\uf05a", "\uf3ed", "\uf15c"};
        ScrollView[] panels = new ScrollView[keys.length];
        LinearLayout[] rows = new LinearLayout[keys.length];
        TextView[] labels = new TextView[keys.length];
        TextView[] glyphs = new TextView[keys.length];
        for (int i = 0; i < keys.length; i++) {
            LinearLayout panel = views.column();
            panel.setPadding(views.dp(24), views.dp(16), views.dp(24), views.dp(24));
            panel.addView(views.label(I18n.text(activity, keys[i]), 22, true));
            TextView text = views.label(sectionText(i), 17, false);
            text.setPadding(0, views.dp(16), 0, 0);
            text.setTextIsSelectable(true);
            Linkify.addLinks(text, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            if (text.getText() instanceof Spannable) {
                Spannable links = (Spannable) text.getText();
                for (URLSpan link : links.getSpans(0, links.length(), URLSpan.class)) {
                    int start = links.getSpanStart(link), end = links.getSpanEnd(link);
                    int flags = links.getSpanFlags(link);
                    links.removeSpan(link);
                    links.setSpan(
                            new URLSpan(link.getURL()) {
                                /**
                                 * Keeps privacy contacts and credits usable on tablets without a
                                 * browser or email client.
                                 */
                                @Override
                                public void onClick(View widget) {
                                    try {
                                        super.onClick(widget);
                                    } catch (ActivityNotFoundException failure) {
                                        Toast.makeText(
                                                        activity,
                                                        I18n.text(
                                                                activity,
                                                                "external_link_unavailable"),
                                                        Toast.LENGTH_LONG)
                                                .show();
                                    }
                                }
                            },
                            start,
                            end,
                            flags);
                }
            }
            text.setLinkTextColor(palette.accent);
            panel.addView(text);
            panels[i] = new ScrollView(activity);
            panels[i].addView(panel);
            content.addView(panels[i], new FrameLayout.LayoutParams(-1, -1));
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(views.dp(60));
            row.setPadding(views.dp(16), views.dp(12), views.dp(12), views.dp(12));
            row.setFocusable(true);
            TextView icon = views.label(icons[i], 20, false);
            icon.setTypeface(style.icons);
            row.addView(icon, new LinearLayout.LayoutParams(views.dp(36), -2));
            TextView label = views.label(I18n.text(activity, keys[i]), 18, false);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            rows[i] = row;
            labels[i] = label;
            glyphs[i] = icon;
            navigation.addView(row, new LinearLayout.LayoutParams(-1, -2));
            final int selected = i;
            row.setOnClickListener(
                    v -> {
                        for (int n = 0; n < panels.length; n++) {
                            boolean active = selected == n;
                            panels[n].setVisibility(active ? View.VISIBLE : View.GONE);
                            rows[n].setSelected(active);
                            rows[n].setBackgroundColor(
                                    active
                                            ? (palette.accent & 0xffffff) | 0x22000000
                                            : Color.TRANSPARENT);
                            labels[n].setTextColor(active ? palette.accent : palette.text);
                            glyphs[n].setTextColor(active ? palette.accent : palette.text);
                        }
                    });
        }
        rows[0].performClick();
        return screen;
    }

    /**
     * Returns localized app information and privacy prose alongside unchanged third-party legal
     * notices.
     */
    private String sectionText(int index) {
        if (index == 0) {
            String version;
            try {
                PackageInfo info =
                        activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                version = I18n.text(activity, "version_build", info.versionName, info.versionCode);
            } catch (PackageManager.NameNotFoundException exception) {
                version = I18n.text(activity, "version_unavailable");
            }
            return version + "\n\n" + I18n.text(activity, "author");
        }
        if (index == 1) return PrivacyNotice.text(activity);
        StringBuilder text = new StringBuilder(I18n.text(activity, "credits_summary"));
        text.append(
                "\n\n"
                        + "Open-Meteo — CC BY 4.0\n"
                        + "https://open-meteo.com/\n"
                        + "https://creativecommons.org/licenses/by/4.0/");
        text.append("\n\nGeoNames\nhttps://www.geonames.org/\nhttps://www.geonames.org/about.html");
        text.append("\n\n").append(I18n.text(activity, "original_assets_credit"));
        text.append("\n\n").append(I18n.text(activity, "interface_icons_credit"));
        text.append(
                "\n\n"
                        + "Font Awesome Free Solid 6.7.2 — SIL OFL 1.1\n"
                        + "https://fontawesome.com/license/free");
        text.append("\n\n").append(I18n.text(activity, "font_conversion_notice"));
        try {
            String[] assets = activity.getAssets().list("");
            if (assets == null) throw new IOException("Asset listing unavailable");
            Arrays.sort(assets);
            for (String name : assets) {
                if (name.endsWith("-OFL.txt")
                        || name.endsWith("-LICENSE.txt")
                        || name.endsWith("-License-Metadata.txt")) {
                    text.append("\n\n—— ").append(name).append(" ——\n").append(readNotice(name));
                }
            }
        } catch (IOException exception) {
            text.append("\n\n").append(I18n.text(activity, "licenses_unavailable"));
        }
        return text.toString();
    }

    /**
     * Reads a bundled UTF-8 legal notice locally, preserving its original language and copyright.
     */
    private String readNotice(String name) throws IOException {
        try (InputStream input = activity.getAssets().open(name);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }
}
