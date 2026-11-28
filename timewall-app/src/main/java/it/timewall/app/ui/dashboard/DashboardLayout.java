package it.timewall.app.ui.dashboard;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
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

import it.timewall.app.R;
import it.timewall.app.configuration.Palette;
import it.timewall.app.localization.I18n;
import it.timewall.app.platform.images.BackgroundBlur;
import it.timewall.app.platform.images.ImageDocuments;
import it.timewall.app.ui.common.DashboardStyle;
import it.timewall.app.ui.common.ViewFactory;
import it.timewall.app.ui.settings.CustomThemeEditor;
import it.timewall.app.ui.weather.DashboardWeather;

import java.util.concurrent.ExecutorService;

/** Builds native dashboard views for portrait and landscape. */
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
    private boolean weatherExpanded;

    public boolean isWeatherExpanded() {
        return weatherExpanded;
    }

    /**
     * Costruisce la dashboard a una colonna in verticale e a due colonne in orizzontale; collega le
     * azioni al controller e carica lo sfondo sul worker senza bloccare il thread UI.
     *
     * @param activity Activity proprietaria delle viste e dei servizi Android
     * @param preferences archivio delle preferenze da leggere o aggiornare
     * @param worker executor destinato alle operazioni bloccanti
     * @param style presentazione risolta della schermata
     * @param views factory dei componenti coerenti con il tema
     * @param customColors palette personalizzata; null quando non applicabile
     * @param actions callback delle azioni della dashboard
     * @param weatherExpanded stato del pannello meteo verticale da ripristinare
     */
    public DashboardLayout(
            Activity activity,
            SharedPreferences preferences,
            ExecutorService worker,
            DashboardStyle style,
            ViewFactory views,
            int[] customColors,
            Actions actions,
            boolean weatherExpanded) {
        this.activity = activity;
        this.views = views;
        this.palette = style.palette;
        this.accentColor = palette.accent;
        this.menuIcons = style.icons;
        this.weatherExpanded = weatherExpanded;
        boolean portrait =
                activity.getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_PORTRAIT;
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
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
        LinearLayout agenda = views.column();
        if (portrait) {
            refreshButton = addControls(root, "TimeWall", actions);
            addSectionDivider(root, 0);
            LinearLayout page = views.column();
            eventsScroll = scroll(root, page);
            page.addView(left);
            page.addView(right);
        } else {
            root.addView(left, new LinearLayout.LayoutParams(0, -1, 3));
            root.addView(right, new LinearLayout.LayoutParams(0, -1, 2));
            refreshButton = addControls(right, I18n.text(activity, "agenda"), actions);
            addSectionDivider(right, COLUMN_INSET);
            eventsScroll = scroll(right, agenda);
        }

        ClockArea clockArea = new ClockArea(activity);
        clockArea.setGravity(Gravity.CENTER);
        featuredList = views.column();
        if (portrait) {
            left.addView(clockArea, new LinearLayout.LayoutParams(-1, -2));
            left.addView(featuredList, new LinearLayout.LayoutParams(-1, -2));
            featuredScroll = null;
        } else {
            left.addView(clockArea, new LinearLayout.LayoutParams(-1, 0, 1));
            featuredList.setGravity(Gravity.CENTER_VERTICAL);
            featuredScroll = scroll(left, featuredList);
            featuredScroll.setFillViewport(true);
        }
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
        if (portrait) {
            updateLayout.width = 0;
            updateLayout.weight = 1;
            weatherLastUpdate.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
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

        int weatherIconSize = portrait ? 50 : 60;
        TextView weatherTemperature = views.label("", portrait ? 38 : 44, false);
        weatherTemperature.setSingleLine(true);
        weatherTemperature.setMinHeight(views.dp(weatherIconSize + 8));
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
        weatherLayout.bottomMargin = views.dp(portrait ? 8 : 22);
        CompactWeatherHeader compactWeather =
                portrait ? new CompactWeatherHeader(weatherDisplay) : null;
        LinearLayout weatherPanel = portrait ? compactWeather.panel : weatherDisplay;
        clockArea.addView(weatherPanel, weatherLayout);
        View weatherSpacer = new android.widget.Space(activity);
        if (!portrait) {
            clockArea.addView(weatherSpacer, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        weather =
                new DashboardWeather(
                        activity,
                        preferences,
                        weatherPanel,
                        weatherPlace,
                        weatherTemperature,
                        weatherSummary,
                        weatherStatus,
                        weatherWind,
                        weatherApparent,
                        weatherPrecipitation,
                        weatherPrecipitationAmount,
                        weatherLastUpdate,
                        portrait ? R.string.widget_weather_last_update : R.string.last_update,
                        weatherRefresh,
                        weatherIconSize,
                        () -> {
                            if (compactWeather != null) {
                                compactWeather.render(
                                        weatherTemperature, weatherSummary, weatherPlace);
                                return;
                            }
                            boolean showing = weatherPanel.getVisibility() == View.VISIBLE;
                            weatherSpacer.setVisibility(showing ? View.VISIBLE : View.GONE);
                            clockArea.setGravity(
                                    showing ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER);
                        });
        int clockTextSize = portrait ? 37 : 74;
        clock =
                customColors == null
                        ? views.label("", clockTextSize, true)
                        : accentLabel("", clockTextSize, true);
        if (customColors != null && customColors.length > CustomThemeEditor.DATE_COLOR_INDEX) {
            clock.setTextColor(customColors[CustomThemeEditor.CLOCK_COLOR_INDEX]);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            clock.setFontFeatureSettings("'tnum'");
        }
        clock.setSingleLine(true);
        clock.setGravity(Gravity.CENTER);
        clockArea.addView(clock);
        date = accentLabel("", portrait ? 24 : 28, false);
        if (customColors != null && customColors.length > CustomThemeEditor.DATE_COLOR_INDEX) {
            date.setTextColor(customColors[CustomThemeEditor.DATE_COLOR_INDEX]);
        }
        date.setGravity(Gravity.CENTER);
        clockArea.addView(date);
        clockArea.bind(weatherDisplay, weatherTemperature, clock, date, weatherIconSize);

        if (portrait) {
            TextView agendaTitle = accentLabel(I18n.text(activity, "agenda"), 24, true);
            agendaTitle.setPadding(views.dp(COLUMN_INSET), views.dp(12), 0, views.dp(6));
            right.addView(agendaTitle);
            addSectionDivider(right, COLUMN_INSET);
            right.addView(agenda);
        }
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

    private Button addControls(LinearLayout parent, String title, Actions actions) {
        LinearLayout eventsHeader = new LinearLayout(activity);
        eventsHeader.setPadding(views.dp(COLUMN_INSET), 0, 0, 0);
        eventsHeader.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(eventsHeader);
        eventsHeader.addView(accentLabel(title, 24, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh =
                views.addHeaderButton(
                        eventsHeader,
                        ViewFactory.REFRESH_GLYPH,
                        I18n.text(activity, "refresh_calendars"),
                        view -> actions.refresh());
        refresh.setBackground(null);
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
        return refresh;
    }

    private final class CompactWeatherHeader {
        private final LinearLayout panel = views.column();
        private static final int PRIMARY_TEXT_SIZE_SP = 16;
        private static final int SECONDARY_TEXT_SIZE_SP = 14;
        private final ImageView weatherIcon = new ImageView(activity);
        private final TextView temperature = views.label("", PRIMARY_TEXT_SIZE_SP, false);
        private final TextView summary = views.label("", PRIMARY_TEXT_SIZE_SP, false);
        private final TextView place = views.secondaryLabel("", SECONDARY_TEXT_SIZE_SP);

        CompactWeatherHeader(LinearLayout details) {
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(views.dp(48));
            row.setPadding(views.dp(COLUMN_INSET), 0, views.dp(COLUMN_INSET), 0);
            TypedValue background = new TypedValue();
            activity.getTheme()
                    .resolveAttribute(android.R.attr.selectableItemBackground, background, true);
            row.setBackgroundResource(background.resourceId);
            panel.addView(row, new LinearLayout.LayoutParams(-1, -2));

            FrameLayout compactSlot = new FrameLayout(activity);
            row.addView(compactSlot, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout compact = new LinearLayout(activity);
            compact.setGravity(Gravity.CENTER_VERTICAL);
            compactSlot.addView(compact, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            weatherIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int iconSize = views.dp(22);
            LinearLayout.LayoutParams iconLayout =
                    new LinearLayout.LayoutParams(iconSize, iconSize);
            iconLayout.setMarginEnd(views.dp(4));
            compact.addView(weatherIcon, iconLayout);
            temperature.setSingleLine(true);
            temperature.setPadding(0, 0, views.dp(6), 0);
            compact.addView(temperature);
            addCompactLabel(compact, summary);
            compact.addView(views.secondaryLabel(" - ", SECONDARY_TEXT_SIZE_SP));
            TextView pin = views.weatherGlyph("\uf3c5");
            pin.setTextSize(SECONDARY_TEXT_SIZE_SP);
            compact.addView(pin);
            addCompactLabel(compact, place);

            TextView title = accentLabel(I18n.text(activity, "weather"), 19, true);
            row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            TextView chevron = views.label("", 16, false);
            chevron.setTypeface(menuIcons);
            chevron.setPadding(views.dp(8), 0, 0, 0);
            chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(chevron);
            panel.addView(details, new LinearLayout.LayoutParams(-1, -2));
            Runnable update =
                    () -> {
                        compactSlot.setVisibility(weatherExpanded ? View.GONE : View.VISIBLE);
                        title.setVisibility(weatherExpanded ? View.VISIBLE : View.GONE);
                        details.setVisibility(weatherExpanded ? View.VISIBLE : View.GONE);
                        chevron.setText(weatherExpanded ? "\uf077" : "\uf078");
                        row.setSelected(weatherExpanded);
                    };
            row.setFocusable(true);
            row.setOnClickListener(
                    view -> {
                        weatherExpanded = !weatherExpanded;
                        update.run();
                    });
            update.run();
        }

        private void addCompactLabel(LinearLayout parent, TextView label) {
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            parent.addView(label, new LinearLayout.LayoutParams(-2, -2, 1));
        }

        void render(TextView sourceTemperature, TextView sourceSummary, TextView sourcePlace) {
            temperature.setVisibility(
                    sourceTemperature.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
            temperature.setText(sourceTemperature.getText());
            summary.setText(sourceSummary.getText());
            place.setText(sourcePlace.getText());
            Drawable fullIcon = sourceTemperature.getCompoundDrawables()[0];
            Drawable.ConstantState iconState =
                    fullIcon == null ? null : fullIcon.getConstantState();
            Drawable icon =
                    iconState == null
                            ? null
                            : iconState.newDrawable(activity.getResources()).mutate();
            if (icon != null) {
                icon.setColorFilter(
                        temperature.getCurrentTextColor(), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            weatherIcon.setImageDrawable(icon);
            weatherIcon.setVisibility(icon == null ? View.GONE : View.VISIBLE);
        }
    }

    private static final class ClockArea extends LinearLayout {
        private LinearLayout weather;
        private TextView temperature, clock, date;
        private float temperatureSize, clockSize, dateSize;
        private int weatherIconSize;
        private final float density;
        private final TextPaint measurementPaint = new TextPaint();

        ClockArea(Activity activity) {
            super(activity);
            setOrientation(VERTICAL);
            density = getResources().getDisplayMetrics().density;
        }

        void bind(
                LinearLayout weather,
                TextView temperature,
                TextView clock,
                TextView date,
                int weatherIconSize) {
            this.weatherIconSize = weatherIconSize;
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
            if (clock != null && MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
                int height =
                        MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                                ? Integer.MAX_VALUE
                                : MeasureSpec.getSize(heightSpec);
                fit(MeasureSpec.getSize(widthSpec), height);
            }
            super.onMeasure(widthSpec, heightSpec);
        }

        private void fit(int width, int height) {
            boolean hasWeather = weather.getVisibility() == VISIBLE;
            boolean weatherInClockArea = weather.getParent() == this;
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
                    if (weatherInClockArea) params.bottomMargin = margin;

                    float scale = 1f - 0.1f * Math.min(4, Math.max(0, step - 2));
                    int padding = dp(weatherInClockArea ? 12 - 4 * spacingStep : 4);
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
                    int minimum = dp(Math.round((weatherIconSize + 8 - 4 * spacingStep) * scale));
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
                        hasWeather && weatherInClockArea
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
                width += dp(weatherIconSize) + temperature.getCompoundDrawablePadding();
            }
            return width;
        }

        private void resizeWeatherIcon(float scale) {
            Drawable icon = temperature.getCompoundDrawables()[0];
            int size = dp(Math.round(weatherIconSize * scale));
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
