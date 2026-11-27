package it.timewall.app.ui.dashboard;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.Layout;
import android.text.TextPaint;
import android.util.TypedValue;
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
import it.timewall.app.platform.images.BackgroundBlur;
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

        void addCalendar();
    }

    private final Activity activity;
    private final ViewFactory views;
    private final Palette palette;
    private final int accentColor;
    private final Typeface menuIcons;
    private static final int COLUMN_INSET = 12, WEATHER_DETAIL_TEXT_SP = 16;
    private static final String BACKGROUND_URI = "dashboardBackgroundUri";
    private static final String ICON_SLIDERS = "\uf1de", ICON_ELLIPSIS_VERTICAL = "\uf142";
    public final FrameLayout dashboardHost;
    public final View dashboardContent;
    public final TextView clock, date, status;
    public final LinearLayout featuredList, todayList, upcomingList;
    public final ScrollView featuredScroll, eventsScroll;
    public final Button refreshButton, addCalendarButton;
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
            int blurLevel = preferences.getInt(BackgroundBlur.PREFERENCE, 0);
            Uri uri = Uri.parse(backgroundUri);
            worker.execute(
                    () -> {
                        Bitmap bitmap = ImageDocuments.readBackground(activity, uri, blurLevel);
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

        ClockArea clockArea = new ClockArea(activity);
        clockArea.setGravity(Gravity.CENTER);
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
        weatherRefresh.setText(ViewFactory.REFRESH_GLYPH);
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
        View weatherSpacer = new android.widget.Space(activity);
        clockArea.addView(weatherSpacer, new LinearLayout.LayoutParams(-1, 0, 1));
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
                        weatherRefresh,
                        () -> {
                            boolean showing = weatherDisplay.getVisibility() == View.VISIBLE;
                            weatherSpacer.setVisibility(showing ? View.VISIBLE : View.GONE);
                            clockArea.setGravity(
                                    showing ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER);
                        });
        clock = customColors == null ? views.label("", 74, true) : accentLabel("", 74, true);
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
        clockArea.bind(weatherDisplay, weatherTemperature, clock, date);

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
                        ViewFactory.REFRESH_GLYPH,
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
        addCalendarButton = new Button(activity);
        addCalendarButton.setText("+ " + I18n.text(activity, "add_calendar"));
        addCalendarButton.setAllCaps(false);
        addCalendarButton.setVisibility(View.GONE);
        addCalendarButton.setOnClickListener(view -> actions.addCalendar());
        LinearLayout.LayoutParams addCalendarParams = new LinearLayout.LayoutParams(-2, -2);
        addCalendarParams.setMarginStart(views.dp(COLUMN_INSET));
        addCalendarParams.topMargin = views.dp(8);
        agenda.addView(addCalendarButton, addCalendarParams);

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

    private static final class ClockArea extends LinearLayout {
        private LinearLayout weather;
        private TextView temperature, clock, date;
        private float temperatureSize, clockSize, dateSize;
        private final float density;
        private final TextPaint measurementPaint = new TextPaint();

        ClockArea(Activity activity) {
            super(activity);
            setOrientation(VERTICAL);
            density = getResources().getDisplayMetrics().density;
        }

        void bind(LinearLayout weather, TextView temperature, TextView clock, TextView date) {
            this.weather = weather;
            this.temperature = temperature;
            this.clock = clock;
            this.date = date;
            temperatureSize = temperature.getTextSize();
            clockSize = clock.getTextSize();
            dateSize = date.getTextSize();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            if (clock != null
                    && MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED
                    && MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
                fit(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
            }
            super.onMeasure(widthSpec, heightSpec);
        }

        private void fit(int width, int height) {
            boolean hasWeather = weather.getVisibility() == VISIBLE;
            int contentSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
            int freeSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            float hourWidth = clockWidth();
            float widthScale =
                    hourWidth > width ? Math.max(1f / clockSize, (width - 1f) / hourWidth) : 1f;
            float temperatureWidth = hasWeather ? temperatureWidth() : 0f;
            for (int step = 0; step <= 8; step++) {
                if (hasWeather) {
                    int spacingStep = Math.min(step, 2);
                    int margin = dp(22 - 10 * spacingStep);
                    LayoutParams params = (LayoutParams) weather.getLayoutParams();
                    params.bottomMargin = margin;

                    float scale = 1f - 0.1f * Math.min(4, Math.max(0, step - 2));
                    int padding = dp(12 - 4 * spacingStep);
                    // Leave room for the two columns of secondary weather metrics.
                    int centerWidth =
                            width
                                    - weather.getPaddingLeft()
                                    - weather.getPaddingRight()
                                    - dp(156)
                                    - padding * 2;
                    if (temperatureWidth > centerWidth && temperatureWidth > 0f) {
                        scale = Math.min(scale, Math.max(0.6f, centerWidth / temperatureWidth));
                    }
                    if (temperature.getPaddingLeft() != padding) {
                        temperature.setPadding(padding, 0, padding, 0);
                    }
                    setSize(temperature, temperatureSize * scale);
                    resizeWeatherIcon(scale);
                    int minimum = dp(Math.round((68 - 4 * spacingStep) * scale));
                    if (temperature.getMinHeight() != minimum) {
                        temperature.setMinHeight(minimum);
                    }
                    weather.measure(
                            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), freeSpec);
                }

                float scale = Math.min(widthScale, 1f - 0.1f * Math.max(0, step - 2));
                setSize(clock, clockSize * scale);
                setSize(date, dateSize * (step <= 4 ? 1f : 1f - 0.1f * (step - 4)));
                clock.measure(contentSpec, freeSpec);
                date.measure(contentSpec, freeSpec);
                int weatherHeight =
                        hasWeather
                                ? weather.getMeasuredHeight()
                                        + ((LayoutParams) weather.getLayoutParams()).bottomMargin
                                : 0;
                if (weatherHeight + clock.getMeasuredHeight() + date.getMeasuredHeight()
                        <= height) {
                    break;
                }
            }
        }

        private float clockWidth() {
            // Reserve the widest digit so the clock does not resize as seconds change.
            measurementPaint.set(clock.getPaint());
            measurementPaint.setTextSize(clockSize);
            float widest = 0;
            for (char digit = '0'; digit <= '9'; digit++) {
                widest = Math.max(widest, measurementPaint.measureText(String.valueOf(digit)));
            }
            CharSequence text = clock.getText();
            float width = Layout.getDesiredWidth(text, measurementPaint);
            for (int i = 0; i < text.length(); i++) {
                char digit = text.charAt(i);
                if (digit >= '0' && digit <= '9') {
                    width += widest - measurementPaint.measureText(String.valueOf(digit));
                }
            }
            return width + clock.getCompoundPaddingLeft() + clock.getCompoundPaddingRight();
        }

        private float temperatureWidth() {
            measurementPaint.set(temperature.getPaint());
            measurementPaint.setTextSize(temperatureSize);
            float width = Layout.getDesiredWidth(temperature.getText(), measurementPaint);
            if (temperature.getCompoundDrawables()[0] != null) {
                width += dp(60) + temperature.getCompoundDrawablePadding();
            }
            return width;
        }

        private void resizeWeatherIcon(float scale) {
            Drawable icon = temperature.getCompoundDrawables()[0];
            int size = dp(Math.round(60 * scale));
            if (icon != null && icon.getBounds().width() != size) {
                icon.setBounds(0, 0, size, size);
                temperature.setCompoundDrawables(icon, null, null, null);
            }
        }

        private static void setSize(TextView view, float size) {
            if (view.getTextSize() != size) {
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            }
        }

        private int dp(int value) {
            return Math.round(value * density);
        }
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
