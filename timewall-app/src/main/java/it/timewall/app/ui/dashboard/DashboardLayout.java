package it.timewall.app.ui.dashboard;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import it.timewall.app.configuration.Palette;
import it.timewall.app.localization.I18n;
import it.timewall.app.platform.images.ImageDocuments;
import it.timewall.app.ui.common.DashboardStyle;
import it.timewall.app.ui.common.ViewFactory;
import it.timewall.app.ui.settings.CustomThemeEditor;
import it.timewall.app.ui.weather.DashboardWeather;

import java.util.concurrent.ExecutorService;

/** Builds native landscape views; event data and Activity lifecycle live elsewhere. */
public final class DashboardLayout {
    public interface Actions {
        /**
         * Richiede al controller l’aggiornamento manuale dei calendari; il layout non esegue
         * direttamente operazioni di rete.
         */
        void refresh();

        /** Richiede al controller di mostrare il filtro dei calendari visibili. */
        void filter();

        /**
         * Richiede al controller il menu contestuale, fornendo la vista a cui ancorarlo.
         *
         * @param anchor vista a cui ancorare il menu
         */
        void menu(View anchor);
    }

    private final Activity activity;
    private final ViewFactory views;
    private final Palette palette;
    private final int accentColor;
    private final Typeface menuIcons;
    private static final int COLUMN_INSET = 12, WEATHER_DETAIL_TEXT_SP = 16;
    private static final String BACKGROUND_URI = "dashboardBackgroundUri";
    private static final String ICON_REFRESH = "\uf021",
            ICON_SLIDERS = "\uf1de",
            ICON_ELLIPSIS_VERTICAL = "\uf142";
    public final FrameLayout dashboardHost;
    public final View dashboardContent;
    public final TextView clock, date, status;
    public final LinearLayout featuredList, todayList, upcomingList;
    public final ScrollView featuredScroll, eventsScroll;
    public final Button refreshButton;
    public final DashboardWeather weather;

    /**
     * Costruisce il layout orizzontale 60/40, i controlli meteo, l’orologio e l’Agenda; collega le
     * azioni al controller e carica lo sfondo sul worker senza bloccare il thread UI.
     *
     * @param activity Activity proprietaria delle viste e dei servizi Android
     * @param preferences archivio delle preferenze da leggere o aggiornare
     * @param worker executor destinato alle operazioni bloccanti
     * @param style presentazione risolta della schermata
     * @param views factory dei componenti coerenti con il tema
     * @param customColors palette personalizzata; null quando non applicabile
     * @param actions callback delle azioni della dashboard
     */
    public DashboardLayout(
            Activity activity,
            SharedPreferences preferences,
            ExecutorService worker,
            DashboardStyle style,
            ViewFactory views,
            int[] customColors,
            Actions actions) {
        this.activity = activity;
        this.views = views;
        this.palette = style.palette;
        this.accentColor = palette.accent;
        this.menuIcons = style.icons;
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(views.dp(20), views.dp(16), views.dp(20), views.dp(16));
        FrameLayout dashboard = new FrameLayout(activity);
        dashboardHost = dashboard;
        dashboardContent = root;
        dashboard.setBackgroundColor(palette.background);
        ImageView background = new ImageView(activity);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int tintAlpha = Color.alpha(palette.background) * 0x88 / 255;
        background.setColorFilter(
                (palette.background & 0xffffff) | (tintAlpha << 24),
                android.graphics.PorterDuff.Mode.SRC_OVER);
        dashboard.addView(background, new FrameLayout.LayoutParams(-1, -1));
        dashboard.addView(root, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(dashboard);
        String backgroundUri = preferences.getString(BACKGROUND_URI, "");
        if (!backgroundUri.isEmpty()) {
            Uri uri = Uri.parse(backgroundUri);
            worker.execute(
                    () -> {
                        Bitmap bitmap = ImageDocuments.readBackground(activity, uri);
                        if (bitmap != null) {
                            activity.runOnUiThread(
                                    () -> {
                                        if (!activity.isDestroyed()) {
                                            background.setImageBitmap(bitmap);
                                        }
                                    });
                        }
                    });
        }
        LinearLayout left = views.column();
        LinearLayout right = views.column();
        root.addView(left, new LinearLayout.LayoutParams(0, -1, 3));
        root.addView(right, new LinearLayout.LayoutParams(0, -1, 2));

        LinearLayout clockArea = views.column();
        clockArea.setGravity(Gravity.CENTER_HORIZONTAL);
        left.addView(clockArea, new LinearLayout.LayoutParams(-1, 0, 1));
        featuredList = views.column();
        featuredList.setGravity(Gravity.CENTER_VERTICAL);
        featuredScroll = scroll(left, featuredList);
        featuredScroll.setFillViewport(true);
        LinearLayout weatherDisplay = views.column();
        weatherDisplay.setPadding(views.dp(COLUMN_INSET), 0, views.dp(COLUMN_INSET), 0);

        LinearLayout locationGroup = new LinearLayout(activity);
        locationGroup.setGravity(Gravity.CENTER_VERTICAL);
        weatherDisplay.addView(locationGroup, new LinearLayout.LayoutParams(-1, -2));
        TextView locationIcon = views.weatherGlyph("\uf3c5");
        locationIcon.setTextSize(16);
        locationGroup.addView(locationIcon);
        TextView weatherPlace = views.secondaryLabel("", WEATHER_DETAIL_TEXT_SP);
        weatherPlace.setSingleLine(true);
        weatherPlace.setEllipsize(android.text.TextUtils.TruncateAt.END);
        locationGroup.addView(weatherPlace, new LinearLayout.LayoutParams(0, -2, 1));
        TextView weatherLastUpdate = views.secondaryLabel("", 13);
        weatherLastUpdate.setGravity(Gravity.RIGHT);
        weatherLastUpdate.setSingleLine(true);
        LinearLayout.LayoutParams updateLayout = new LinearLayout.LayoutParams(-2, -2);
        updateLayout.leftMargin = views.dp(8);
        locationGroup.addView(weatherLastUpdate, updateLayout);
        Button weatherRefresh = new Button(activity);
        weatherRefresh.setTypeface(menuIcons);
        weatherRefresh.setText(ICON_REFRESH);
        weatherRefresh.setTextColor(palette.text);
        weatherRefresh.setTextSize(14);
        weatherRefresh.setContentDescription(I18n.text(activity, "refresh_weather"));
        weatherRefresh.setGravity(Gravity.CENTER);
        weatherRefresh.setBackground(null);
        weatherRefresh.setPadding(0, 0, 0, 0);
        locationGroup.addView(
                weatherRefresh, new LinearLayout.LayoutParams(views.dp(36), views.dp(36)));

        LinearLayout weatherGrid = new LinearLayout(activity);
        weatherDisplay.addView(weatherGrid, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout weatherLeft = views.column();
        weatherGrid.addView(weatherLeft, new LinearLayout.LayoutParams(0, -1, 1));
        TextView weatherWind = addWeatherMetric(weatherLeft, false, "\uf72e");
        TextView weatherApparent = addWeatherMetric(weatherLeft, false, "\uf2c9", "\uf183");

        TextView weatherTemperature = views.label("", 44, false);
        weatherTemperature.setSingleLine(true);
        weatherTemperature.setMinHeight(views.dp(68));
        weatherTemperature.setGravity(Gravity.CENTER);
        weatherTemperature.setPadding(views.dp(12), 0, views.dp(12), 0);
        weatherGrid.addView(weatherTemperature, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout weatherRight = views.column();
        weatherGrid.addView(weatherRight, new LinearLayout.LayoutParams(0, -1, 1));
        TextView weatherPrecipitation = addWeatherMetric(weatherRight, true, "\uf0e9");
        TextView weatherPrecipitationAmount = addWeatherMetric(weatherRight, true, "\uf043");

        TextView weatherSummary = views.label("", 20, false);
        weatherSummary.setGravity(Gravity.CENTER);
        weatherDisplay.addView(weatherSummary, new LinearLayout.LayoutParams(-1, -2));

        TextView weatherStatus = views.secondaryLabel("", 22);
        weatherStatus.setGravity(Gravity.CENTER);
        weatherDisplay.addView(weatherStatus, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams weatherLayout = new LinearLayout.LayoutParams(-1, -2);
        weatherLayout.bottomMargin = views.dp(22);
        clockArea.addView(weatherDisplay, weatherLayout);
        clockArea.addView(
                new android.widget.Space(activity), new LinearLayout.LayoutParams(-1, 0, 1));
        weather =
                new DashboardWeather(
                        activity,
                        preferences,
                        weatherDisplay,
                        weatherPlace,
                        weatherTemperature,
                        weatherSummary,
                        weatherStatus,
                        weatherWind,
                        weatherApparent,
                        weatherPrecipitation,
                        weatherPrecipitationAmount,
                        weatherLastUpdate,
                        weatherRefresh);
        clock = accentLabel("", 74, true);
        if (customColors != null && customColors.length > CustomThemeEditor.DATE_COLOR_INDEX) {
            clock.setTextColor(customColors[CustomThemeEditor.CLOCK_COLOR_INDEX]);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            clock.setFontFeatureSettings("'tnum'");
        }
        clock.setSingleLine(true);
        clock.setGravity(Gravity.CENTER);
        clockArea.addView(clock);
        date = accentLabel("", 28, false);
        if (customColors != null && customColors.length > CustomThemeEditor.DATE_COLOR_INDEX) {
            date.setTextColor(customColors[CustomThemeEditor.DATE_COLOR_INDEX]);
        }
        date.setGravity(Gravity.CENTER);
        clockArea.addView(date);

        LinearLayout eventsHeader = new LinearLayout(activity);
        eventsHeader.setPadding(views.dp(COLUMN_INSET), 0, 0, 0);
        eventsHeader.setGravity(Gravity.CENTER_VERTICAL);
        right.addView(eventsHeader);
        eventsHeader.addView(
                accentLabel(I18n.text(activity, "agenda"), 24, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        refreshButton =
                views.addHeaderButton(
                        eventsHeader,
                        ICON_REFRESH,
                        I18n.text(activity, "refresh_calendars"),
                        view -> actions.refresh());
        refreshButton.setBackground(null);
        views.addHeaderButton(
                        eventsHeader,
                        ICON_SLIDERS,
                        I18n.text(activity, "filter_events"),
                        view -> actions.filter())
                .setBackground(null);
        views.addHeaderButton(
                        eventsHeader,
                        ICON_ELLIPSIS_VERTICAL,
                        I18n.text(activity, "open_menu"),
                        actions::menu)
                .setBackground(null);
        addSectionDivider(right, COLUMN_INSET);

        LinearLayout agenda = views.column();
        eventsScroll = scroll(right, agenda);
        status = views.secondaryLabel("", 13);
        status.setPadding(views.dp(COLUMN_INSET), 0, 0, 0);
        agenda.addView(status);

        LinearLayout today = views.column();
        agenda.addView(today);
        TextView todayTitle = accentLabel(I18n.text(activity, "today"), 21, true);
        todayTitle.setPadding(views.dp(COLUMN_INSET), 0, 0, 0);
        today.addView(todayTitle);
        todayList = views.column();
        todayList.setPadding(views.dp(COLUMN_INSET), 0, 0, 0);
        todayList.setClipToPadding(false);
        today.addView(todayList);

        LinearLayout upcoming = views.column();
        upcoming.setPadding(views.dp(COLUMN_INSET), views.dp(12), 0, 0);
        agenda.addView(upcoming);
        upcoming.addView(accentLabel(I18n.text(activity, "upcoming"), 21, true));
        upcomingList = views.column();
        upcoming.addView(upcomingList);
    }

    /**
     * Crea una metrica laterale del meteo con uno o più glifi. Posiziona le icone a destra o a
     * sinistra secondo la colonna e restituisce la label destinata al valore.
     *
     * @param column colonna meteo in cui inserire la metrica
     * @param alignRight true per collocare icone a destra dei valori
     * @param glyphs glifi da affiancare alla metrica
     * @return label configurata secondo lo stile descritto
     */
    private TextView addWeatherMetric(LinearLayout column, boolean alignRight, String... glyphs) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(row, new LinearLayout.LayoutParams(-1, -2, 1));

        TextView value = views.secondaryLabel("", WEATHER_DETAIL_TEXT_SP);
        LinearLayout.LayoutParams valueLayout = new LinearLayout.LayoutParams(0, -2, 1);
        if (alignRight) {
            value.setGravity(Gravity.RIGHT);
            valueLayout.rightMargin = views.dp(6);
            row.addView(value, valueLayout);
        }
        for (String glyph : glyphs) {
            TextView icon = views.weatherGlyph(glyph);
            if (alignRight) {
                icon.setPadding(0, 0, 0, 0);
                icon.setGravity(Gravity.CENTER);
                row.addView(icon, new LinearLayout.LayoutParams(views.dp(26), -2));
            } else {
                row.addView(icon);
            }
        }
        if (!alignRight) {
            row.addView(value, valueLayout);
        }
        return value;
    }

    /**
     * Aggiunge al contenitore una lista scrollabile con peso verticale, mantenendo indipendente il
     * contenuto ricevuto.
     *
     * @param parent contenitore o selettore che richiede la vista
     * @param list contenitore in cui inserire riquadri o messaggi
     * @return contenitore scrollabile aggiunto alla schermata
     */
    private ScrollView scroll(LinearLayout parent, LinearLayout list) {
        ScrollView scroll = new ScrollView(activity);
        parent.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        scroll.addView(list);
        return scroll;
    }

    /**
     * Aggiunge un divisore della palette con il rientro orizzontale richiesto, senza modificare il
     * titolo della sezione.
     *
     * @param section sezione JSON o nome della sezione da riportare
     * @param inset rientro del divisore in dp
     */
    private void addSectionDivider(LinearLayout section, int inset) {
        View divider = new View(activity);
        divider.setBackgroundColor(palette.border);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, views.dp(1));
        params.leftMargin = views.dp(inset);
        section.addView(divider, params);
    }

    /**
     * Crea un titolo della dashboard con il colore principale del tema.
     *
     * @param text testo da visualizzare o analizzare
     * @param size dimensione in sp per il testo o in dp per il pallino, secondo il controllo
     * @param bold true per una label in grassetto
     * @return label configurata secondo lo stile descritto
     */
    private TextView accentLabel(String text, int size, boolean bold) {
        TextView view = views.label(text, size, bold);
        view.setTextColor(accentColor);
        return view;
    }
}
