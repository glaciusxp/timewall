package it.timewall.app.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import it.timewall.app.BuildConfig;
import it.timewall.app.configuration.DashboardFont;
import it.timewall.app.configuration.DashboardTheme;
import it.timewall.app.configuration.Palette;
import it.timewall.app.domain.model.Feed;
import it.timewall.app.localization.DashboardDates;
import it.timewall.app.localization.I18n;
import it.timewall.app.localization.TimeDisplay;
import it.timewall.app.ui.common.DashboardStyle;
import it.timewall.app.ui.common.ViewFactory;
import it.timewall.app.ui.weather.DashboardWeather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Owns the unsaved settings draft and its screen lifecycle. */
public final class SettingsScreen {
    private static final String FONT_PREFERENCE = "dashboardFont";
    private static final String SHOW_SECONDS_PREFERENCE = "clock.showSeconds";
    private static final String ICON_CALENDAR = "\uf073";
    private static final String ICON_SETTINGS = "\uf013";

    public interface Host {
        /**
         * Clears saved calendar caches immediately, invoking completion on the UI thread without
         * saving the draft.
         */
        void clearCalendarCache(Runnable completion);

        /**
         * Comunica che la schermata impostazioni è pronta per essere inserita nel contenitore della
         * dashboard.
         *
         * @param screen vista della schermata impostazioni da inserire o rimuovere
         */
        void opened(View screen);

        /**
         * Comunica che la schermata deve essere rimossa e la dashboard resa nuovamente visibile.
         *
         * @param screen vista della schermata impostazioni da inserire o rimuovere
         */
        void closed(View screen);

        /**
         * Comunica il completamento del salvataggio fornendo la lista dei calendari e la necessità
         * di ricreare l’Activity per applicare lingua o aspetto.
         *
         * @param feeds calendari configurati o bozza di calendari da elaborare
         * @param recreate true se le modifiche richiedono la ricreazione dell’Activity
         */
        void saved(List<Feed> feeds, boolean recreate);
    }

    private final Activity activity;
    private final DashboardDates dates;
    private final ViewFactory views;
    private final SharedPreferences preferences;
    private final Host host;
    private final DashboardWeather weather;
    private final List<Feed> feeds;
    private final Palette palette;
    private final Typeface dashboardTypeface, menuIcons;
    private final int accentColor;
    private final DashboardTheme selectedTheme;
    private final DashboardFont selectedFont;
    private final boolean showSeconds, use24Hour, darkTheme;
    private final int clockDateFormat;
    private final java.util.concurrent.ExecutorService worker;
    private BackgroundImageEditor backgroundSettings;
    private Runnable closeSettings, requestSettingsExit;
    private AlertDialog settingsExitDialog;

    /**
     * Crea il controller delle impostazioni con una copia della lista calendari e le opzioni
     * correnti di presentazione; i widget vengono costruiti soltanto da show.
     *
     * @param activity Activity proprietaria delle viste e dei servizi Android
     * @param preferences archivio delle preferenze da leggere o aggiornare
     * @param host destinatario delle callback di apertura, chiusura e salvataggio
     * @param weather controller meteo che fornisce e applica la propria bozza di impostazioni
     * @param feeds calendari configurati o bozza di calendari da elaborare
     * @param style presentazione risolta della schermata
     * @param worker executor destinato alle operazioni bloccanti
     */
    public SettingsScreen(
            Activity activity,
            SharedPreferences preferences,
            Host host,
            DashboardWeather weather,
            List<Feed> feeds,
            DashboardStyle style,
            java.util.concurrent.ExecutorService worker) {
        Palette palette = style.palette;
        Typeface typeface = style.font, menuIcons = style.icons;
        boolean darkTheme = style.dark;
        this.views = new ViewFactory(activity, palette, typeface, menuIcons, darkTheme);
        this.dates = new DashboardDates(activity);
        this.activity = activity;
        this.preferences = preferences;
        this.host = host;
        this.weather = weather;
        this.feeds = new ArrayList<>(feeds);
        this.palette = palette;
        this.dashboardTypeface = typeface;
        this.menuIcons = menuIcons;
        this.accentColor = palette.accent;
        this.selectedTheme = style.theme;
        this.selectedFont = style.selectedFont;
        this.showSeconds = preferences.getBoolean(SHOW_SECONDS_PREFERENCE, true);
        this.use24Hour = preferences.getBoolean(TimeDisplay.HOUR_24, true);
        this.clockDateFormat = preferences.getInt(TimeDisplay.DATE_FORMAT, 0);
        this.darkTheme = darkTheme;
        this.worker = worker;
    }

    /**
     * Gestisce Indietro tramite la procedura di uscita, che può chiedere conferma per modifiche non
     * salvate. Restituisce false se non c’è una schermata aperta da gestire.
     *
     * @return true se la richiesta Indietro è stata gestita dalla schermata aperta; false
     *     altrimenti
     */
    public boolean back() {
        if (requestSettingsExit == null) return false;
        requestSettingsExit.run();
        return true;
    }

    /**
     * Inoltra l’URI scelto nel file picker all’editor dello sfondo, se ancora presente.
     *
     * @param uri URI dell’immagine esposto dal provider documenti
     */
    public void selectBackground(Uri uri) {
        if (backgroundSettings != null) backgroundSettings.select(uri);
    }

    /**
     * Chiude la schermata aperta attraverso la stessa procedura di pulizia usata all’uscita, senza
     * salvare la bozza.
     */
    public void dispose() {
        if (closeSettings != null) closeSettings.run();
    }

    /**
     * Crea le sezioni e la sidebar delle impostazioni con una bozza locale. Collega navigazione,
     * validazione, confronto delle modifiche e salvataggio; aggiorna preferenze e controller
     * soltanto dopo una conferma valida.
     */
    public void show() {
        if (closeSettings != null) {
            return;
        }
        LinearLayout[] sections = new LinearLayout[5];
        ScrollView[] sectionScrolls = new ScrollView[5];
        for (int index = 0; index < sections.length; index++) {
            sections[index] = views.column();
            sections[index].setPadding(views.dp(24), views.dp(16), views.dp(24), views.dp(24));
            sectionScrolls[index] = new ScrollView(activity);
            sectionScrolls[index].setFillViewport(true);
            sectionScrolls[index].addView(sections[index]);
        }
        List<Feed> pendingFeeds = new ArrayList<>(feeds);
        Button clearCache = new Button(activity);
        clearCache.setText(I18n.text(activity, "clear_calendar_cache"));
        clearCache.setOnClickListener(
                v -> {
                    clearCache.setEnabled(false);
                    host.clearCalendarCache(() -> clearCache.setEnabled(true));
                });
        LinearLayout calendarList = views.column();
        sections[1].addView(calendarList);
        new CalendarSettingsSection(activity, views).render(calendarList, pendingFeeds);
        TextView cacheHint = views.secondaryLabel(I18n.text(activity, "calendar_cache_hint"), 15);
        views.addSettingView(sections[1], cacheHint, false, 6);
        sections[1].addView(clearCache);
        LinearLayout fields = sections[0];
        views.addSettingHeading(fields, I18n.text(activity, "language"), true);
        SharedPreferences preferences = this.preferences;
        String selectedLanguage = preferences.getString(I18n.LANGUAGE_PREFERENCE, I18n.SYSTEM);
        List<String> languages = new ArrayList<>();
        languages.add(I18n.SYSTEM);
        languages.addAll(Arrays.asList(I18n.supportedLanguages(activity)));
        String[] languageNames = new String[languages.size()];
        for (int index = 0; index < languages.size(); index++) {
            String code = languages.get(index);
            languageNames[index] =
                    I18n.text(
                            activity,
                            I18n.SYSTEM.equals(code) ? "language_system" : "language_name_" + code);
        }
        Spinner languagePicker = new Spinner(activity);
        ArrayAdapter<String> languageChoices = views.settingChoices(languageNames);
        languagePicker.setAdapter(languageChoices);
        languagePicker.setSelection(Math.max(0, languages.indexOf(selectedLanguage)));
        fields.addView(languagePicker);
        fields = sections[2];
        views.addSettingHeading(fields, I18n.text(activity, "theme"), true);

        List<DashboardTheme> themes = new ArrayList<>(Arrays.asList(DashboardTheme.values()));
        themes.remove(DashboardTheme.CUSTOM);
        java.text.Collator collator = java.text.Collator.getInstance(I18n.locale(activity));
        Collections.sort(
                themes,
                (first, second) ->
                        collator.compare(
                                I18n.text(activity, first.name), I18n.text(activity, second.name)));
        themes.add(DashboardTheme.CUSTOM);
        if (BuildConfig.FREE && !BuildConfig.PLAY_PREVIEW) {
            java.util.Iterator<DashboardTheme> iterator = themes.iterator();
            while (iterator.hasNext()) {
                if (!iterator.next().availableInFreeEdition()) iterator.remove();
            }
        }
        String[] names = new String[themes.size()];
        for (int index = 0; index < themes.size(); index++) {
            names[index] = I18n.text(activity, themes.get(index).name);
        }
        Spinner themePicker = new Spinner(activity);
        ArrayAdapter<String> choices =
                choices(names, position -> themes.get(position).availableInFreeEdition());
        themePicker.setAdapter(choices);
        themePicker.setSelection(themes.indexOf(selectedTheme));
        fields.addView(themePicker);

        BackgroundImageEditor imageSettings =
                BuildConfig.FREE
                        ? null
                        : new BackgroundImageEditor(activity, views, worker, preferences);

        CustomThemeEditor customTheme =
                BuildConfig.FREE ? null : new CustomThemeEditor(activity, views, preferences);
        if (customTheme != null) {
            views.addSettingView(fields, customTheme, false, 0);
            customTheme.setVisibility(
                    selectedTheme == DashboardTheme.CUSTOM ? View.VISIBLE : View.GONE);
        }

        if (imageSettings != null) {
            views.addSettingView(fields, imageSettings.view, false, 0);
        } else if (BuildConfig.PLAY_PREVIEW) {
            TextView background = views.settingHeading(I18n.text(activity, "background"));
            background.setTextColor(palette.secondaryText);
            background.setEnabled(false);
            views.addSettingView(fields, ProBadge.row(activity, views, background), false, 0);
        }
        fields = sections[0];
        views.addSettingHeading(fields, I18n.text(activity, "font"), false);
        DashboardFont[] fonts =
                BuildConfig.FREE && !BuildConfig.PLAY_PREVIEW
                        ? new DashboardFont[] {
                            DashboardFont.SYSTEM_ROBOTO,
                            DashboardFont.ROBOTO_CONDENSED,
                            DashboardFont.ROBOTO_SLAB,
                            DashboardFont.SHANTELL_SANS
                        }
                        : DashboardFont.values();
        String[] fontNames = new String[fonts.length];
        for (int index = 0; index < fonts.length; index++) {
            fontNames[index] =
                    fonts[index] == DashboardFont.SYSTEM_ROBOTO
                            ? I18n.text(activity, fonts[index].label)
                            : fonts[index].label;
        }
        Spinner fontPicker = new Spinner(activity);
        ArrayAdapter<String> fontChoices =
                choices(fontNames, position -> fonts[position].availableInFreeEdition());
        fontPicker.setAdapter(fontChoices);
        fontPicker.setSelection(Arrays.asList(fonts).indexOf(selectedFont));
        fields.addView(fontPicker);
        fields = sections[4];
        CheckBox secondsToggle = new CheckBox(activity);
        secondsToggle.setText(I18n.text(activity, "show_seconds"));
        secondsToggle.setChecked(showSeconds);
        views.styleSettingInput(secondsToggle);
        fields.addView(secondsToggle);

        views.addSettingHeading(fields, I18n.text(activity, "time_format"), false);
        Spinner timeFormatPicker = new Spinner(activity);
        ArrayAdapter<String> timeChoices =
                views.settingChoices(
                        I18n.text(activity, "hour_24"), I18n.text(activity, "hour_12"));
        timeFormatPicker.setAdapter(timeChoices);
        timeFormatPicker.setSelection(use24Hour ? 0 : 1);
        fields.addView(timeFormatPicker);
        views.addSettingHeading(fields, I18n.text(activity, "date_format"), false);
        String[] datePreviews = new String[4];
        long previewTime = System.currentTimeMillis();
        for (int index = 0; index < datePreviews.length; index++) {
            datePreviews[index] =
                    index == 0
                            ? dates.localizedDate(previewTime, "clock_date_pattern")
                            : dates.format(previewTime, TimeDisplay.datePattern(activity, index));
        }
        Spinner dateFormatPicker = new Spinner(activity);
        ArrayAdapter<String> dateChoices = views.settingChoices(datePreviews);
        dateFormatPicker.setAdapter(dateChoices);
        dateFormatPicker.setSelection(
                clockDateFormat >= 0 && clockDateFormat < 4 ? clockDateFormat : 0);
        fields.addView(dateFormatPicker);
        fields = sections[3];
        TextView weatherHeading = views.settingHeading(I18n.text(activity, "weather"));
        DashboardWeather.Settings weatherSettings = weather == null ? null : weather.new Settings();
        if (weatherSettings != null) {
            fields.addView(weatherHeading);
            fields.addView(weatherSettings.createView(views, views.weatherGlyph("\uf3c5")));
        } else if (BuildConfig.PLAY_PREVIEW) {
            fields.addView(ProBadge.row(activity, views, weatherHeading));
            fields.addView(ProBadge.weatherFields(activity, views));
        }

        LinearLayout screen = views.column();
        android.util.TypedValue backgroundColor = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.colorBackground, backgroundColor, true);
        screen.setBackgroundColor(backgroundColor.data);
        screen.setClickable(true);
        screen.setFocusableInTouchMode(true);

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(views.dp(8), 0, views.dp(8), 0);
        int headerColor = palette.accent | 0xff000000;
        int headerTextColor = ViewFactory.contrastingText(headerColor);
        header.setBackgroundColor(headerColor);
        screen.addView(header, new LinearLayout.LayoutParams(-1, views.dp(64)));
        Button back =
                views.addHeaderButton(
                        header,
                        "\uf053",
                        I18n.text(activity, "back"),
                        view -> {
                            if (requestSettingsExit != null) {
                                requestSettingsExit.run();
                            }
                        });
        back.setBackground(null);
        back.setTextColor(headerTextColor);
        TextView heading = views.label(I18n.text(activity, "settings"), 24, true);
        heading.setTextColor(headerTextColor);
        heading.setGravity(Gravity.CENTER);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button save = new Button(activity);
        save.setText(I18n.text(activity, "save"));
        save.setTextColor(
                new ColorStateList(
                        new int[][] {{-android.R.attr.state_enabled}, {}},
                        new int[] {(headerTextColor & 0xffffff) | 0x66000000, headerTextColor}));
        save.setBackground(null);
        save.setPadding(views.dp(16), 0, views.dp(16), 0);
        header.addView(save, new LinearLayout.LayoutParams(-2, -1));
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
        String[] sectionKeys = {"general", "calendars", "personalization", "weather", "date_time"};
        String[] sectionIcons = {ICON_SETTINGS, ICON_CALENDAR, "\uf53f", "\uf0c2", "\uf017"};
        LinearLayout[] navigationRows = new LinearLayout[sections.length];
        TextView[] navigationLabels = new TextView[sections.length];
        TextView[] navigationIcons = new TextView[sections.length];
        Runnable[] selectSection = new Runnable[sections.length];
        for (int index = 0; index < sections.length; index++) {
            content.addView(sectionScrolls[index], new FrameLayout.LayoutParams(-1, -1));
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(views.dp(16), views.dp(12), views.dp(12), views.dp(12));
            row.setMinimumHeight(views.dp(60));
            row.setFocusable(true);
            TextView icon = views.label(sectionIcons[index], 20, false);
            icon.setTypeface(menuIcons);
            row.addView(icon, new LinearLayout.LayoutParams(views.dp(36), -2));
            TextView text = views.label(I18n.text(activity, sectionKeys[index]), 18, false);
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            if (index == 3) {
                if (BuildConfig.PLAY_PREVIEW) ProBadge.add(activity, views, row);
                else if (BuildConfig.FREE) row.setVisibility(View.GONE);
            }
            navigationRows[index] = row;
            navigationLabels[index] = text;
            navigationIcons[index] = icon;
            navigation.addView(row, new LinearLayout.LayoutParams(-1, -2));
            final int selected = index;
            selectSection[index] =
                    () -> {
                        for (int item = 0; item < sections.length; item++) {
                            boolean active = item == selected;
                            sectionScrolls[item].setVisibility(active ? View.VISIBLE : View.GONE);
                            navigationRows[item].setSelected(active);
                            navigationRows[item].setBackgroundColor(
                                    active
                                            ? (palette.accent & 0xffffff) | 0x22000000
                                            : Color.TRANSPARENT);
                            int color = active ? palette.accent : palette.text;
                            navigationLabels[item].setTextColor(color);
                            navigationIcons[item].setTextColor(color);
                        }
                        screen.requestFocus();
                        android.view.inputmethod.InputMethodManager keyboard =
                                (android.view.inputmethod.InputMethodManager)
                                        activity.getSystemService(
                                                android.content.Context.INPUT_METHOD_SERVICE);
                        keyboard.hideSoftInputFromWindow(screen.getWindowToken(), 0);
                    };
            row.setOnClickListener(view -> selectSection[selected].run());
        }
        selectSection[0].run();
        backgroundSettings = imageSettings;
        closeSettings =
                () -> {
                    if (settingsExitDialog != null) {
                        settingsExitDialog.dismiss();
                    }
                    if (weatherSettings != null) weatherSettings.cancelSearch();
                    if (imageSettings != null) imageSettings.dispose();
                    if (backgroundSettings == imageSettings) {
                        backgroundSettings = null;
                    }
                    android.view.inputmethod.InputMethodManager keyboard =
                            (android.view.inputmethod.InputMethodManager)
                                    activity.getSystemService(
                                            android.content.Context.INPUT_METHOD_SERVICE);
                    keyboard.hideSoftInputFromWindow(screen.getWindowToken(), 0);
                    host.closed(screen);
                    closeSettings = null;
                    requestSettingsExit = null;
                };
        int initialLanguage = languagePicker.getSelectedItemPosition();
        int initialTheme = themePicker.getSelectedItemPosition();
        int initialFont = fontPicker.getSelectedItemPosition();
        int initialTimeFormat = timeFormatPicker.getSelectedItemPosition();
        int initialDateFormat = dateFormatPicker.getSelectedItemPosition();
        boolean initialSeconds = secondsToggle.isChecked();
        String initialCalendars = serializeFeeds(pendingFeeds);
        String[] initialColors = customTheme == null ? null : customTheme.inputValues();
        String initialWeather = weatherSettings == null ? null : weatherSettings.snapshot();
        requestSettingsExit =
                () -> {
                    boolean changed =
                            languagePicker.getSelectedItemPosition() != initialLanguage
                                    || themePicker.getSelectedItemPosition() != initialTheme
                                    || fontPicker.getSelectedItemPosition() != initialFont
                                    || timeFormatPicker.getSelectedItemPosition()
                                            != initialTimeFormat
                                    || dateFormatPicker.getSelectedItemPosition()
                                            != initialDateFormat
                                    || secondsToggle.isChecked() != initialSeconds
                                    || !serializeFeeds(pendingFeeds).equals(initialCalendars)
                                    || (customTheme != null
                                            && !Arrays.equals(
                                                    customTheme.inputValues(), initialColors))
                                    || (weatherSettings != null
                                            && !weatherSettings.snapshot().equals(initialWeather))
                                    || (imageSettings != null && imageSettings.changed());
                    if (!changed) {
                        closeSettings.run();
                        return;
                    }
                    if (settingsExitDialog != null) {
                        return;
                    }
                    settingsExitDialog =
                            new AlertDialog.Builder(activity)
                                    .setTitle(I18n.text(activity, "unsaved_changes"))
                                    .setMessage(I18n.text(activity, "discard_settings_message"))
                                    .setPositiveButton(
                                            I18n.text(activity, "exit"),
                                            (dialog, which) -> closeSettings.run())
                                    .setNegativeButton(I18n.text(activity, "cancel"), null)
                                    .create();
                    settingsExitDialog.setOnDismissListener(dialog -> settingsExitDialog = null);
                    settingsExitDialog.show();
                };
        host.opened(screen);
        activity.getWindow()
                .setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        screen.requestFocus();
        Runnable updateThemeFields =
                () -> {
                    boolean custom =
                            themes.get(themePicker.getSelectedItemPosition())
                                    == DashboardTheme.CUSTOM;
                    if (customTheme != null)
                        customTheme.setVisibility(custom ? View.VISIBLE : View.GONE);
                    save.setEnabled(
                            (imageSettings == null || !imageSettings.loading)
                                    && (!custom
                                            || (customTheme != null
                                                    && customTheme.readColors() != null)));
                };
        if (imageSettings != null) imageSettings.onChange = updateThemeFields;
        if (customTheme != null) customTheme.setOnChange(updateThemeFields);
        themePicker.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    /**
                     * Quando cambia il tema selezionato aggiorna la visibilità dei colori
                     * personalizzati e la validità del pulsante Salva.
                     *
                     * @param parent contenitore o selettore che richiede la vista
                     * @param view vista della voce che ha generato la selezione
                     * @param position indice della voce selezionata o della riga da visualizzare
                     * @param id identificativo della voce o risorsa richiesta
                     */
                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {
                        updateThemeFields.run();
                    }

                    /**
                     * Non modifica la bozza quando il selettore non ha una voce attiva; la
                     * configurazione corrente rimane invariata.
                     *
                     * @param parent contenitore o selettore che richiede la vista
                     */
                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                });
        updateThemeFields.run();
        save.setOnClickListener(
                view -> {
                    // Older complete profiles may still contain more than the current limit.
                    if (pendingFeeds.size() > Math.max(feeds.size(), BuildConfig.CALENDAR_LIMIT)) {
                        selectSection[1].run();
                        return;
                    }
                    DashboardTheme theme = themes.get(themePicker.getSelectedItemPosition());
                    int[] colors =
                            theme == DashboardTheme.CUSTOM && customTheme != null
                                    ? customTheme.readColors()
                                    : null;
                    if ((imageSettings != null && imageSettings.loading)
                            || (theme == DashboardTheme.CUSTOM && colors == null)) {
                        selectSection[2].run();
                        return;
                    }
                    if (weatherSettings != null && !weatherSettings.validate()) {
                        selectSection[3].run();
                        return;
                    }
                    if (imageSettings != null && !imageSettings.retainAccess()) {
                        selectSection[2].run();
                        return;
                    }
                    DashboardFont font = fonts[fontPicker.getSelectedItemPosition()];
                    if (BuildConfig.FREE
                            && (!theme.availableInFreeEdition()
                                    || !font.availableInFreeEdition())) {
                        return;
                    }
                    if (!font.file.isEmpty()) {
                        try {
                            Typeface.createFromAsset(activity.getAssets(), font.file);
                        } catch (RuntimeException exception) {
                            Toast.makeText(
                                            activity,
                                            I18n.text(activity, "font_invalid"),
                                            Toast.LENGTH_LONG)
                                    .show();
                            return;
                        }
                    }
                    String serializedFeeds = serializeFeeds(pendingFeeds);
                    String language = languages.get(languagePicker.getSelectedItemPosition());
                    boolean changed =
                            theme != selectedTheme
                                    || font != selectedFont
                                    || (imageSettings != null && imageSettings.changed())
                                    || !language.equals(selectedLanguage);
                    SharedPreferences.Editor editor =
                            preferences
                                    .edit()
                                    .putInt("theme", theme.id)
                                    .putString(FONT_PREFERENCE, font.file)
                                    .putBoolean(SHOW_SECONDS_PREFERENCE, secondsToggle.isChecked())
                                    .putString(I18n.LANGUAGE_PREFERENCE, language)
                                    .putBoolean(
                                            TimeDisplay.HOUR_24,
                                            timeFormatPicker.getSelectedItemPosition() == 0)
                                    .putInt(
                                            TimeDisplay.DATE_FORMAT,
                                            dateFormatPicker.getSelectedItemPosition())
                                    .putString("feeds", serializedFeeds);
                    if (!BuildConfig.FREE && colors != null) {
                        changed |=
                                !Arrays.equals(colors, CustomThemeEditor.loadColors(preferences));
                        CustomThemeEditor.saveColors(editor, colors);
                    }
                    if (imageSettings != null) imageSettings.save(editor);
                    editor.apply();
                    if (weatherSettings != null) weatherSettings.save();
                    if (imageSettings != null) imageSettings.releasePrevious();
                    closeSettings.run();
                    host.saved(pendingFeeds, changed);
                });
    }

    private interface FreeChoice {
        boolean allows(int position);
    }

    private ArrayAdapter<String> choices(String[] labels, FreeChoice freeChoice) {
        return views.new SettingChoices(labels) {
            @Override
            public boolean areAllItemsEnabled() {
                return !BuildConfig.PLAY_PREVIEW;
            }

            @Override
            public boolean isEnabled(int position) {
                return !BuildConfig.PLAY_PREVIEW || freeChoice.allows(position);
            }

            @Override
            public View getDropDownView(int position, View recycled, ViewGroup parent) {
                if (!BuildConfig.PLAY_PREVIEW)
                    return super.getDropDownView(position, recycled, parent);
                TextView item = (TextView) super.getDropDownView(position, null, parent);
                if (isEnabled(position)) return item;
                item.setEnabled(false);
                ProBadge.markChoice(activity, views, item);
                return item;
            }
        };
    }

    /**
     * Serializza la bozza dei calendari nel JSON storico con nome, indirizzo e colore; il testo
     * serve sia al salvataggio sia al confronto delle modifiche.
     *
     * @param pendingFeeds lista dei calendari della bozza non ancora salvata
     * @return array JSON della bozza con nome, indirizzo e colore di ogni calendario
     */
    private String serializeFeeds(List<Feed> pendingFeeds) {
        JSONArray array = new JSONArray();
        try {
            for (Feed feed : pendingFeeds) {
                array.put(
                        new JSONObject()
                                .put("name", feed.name)
                                .put("address", feed.address)
                                .put("color", feed.color));
            }
            return array.toString();
        } catch (JSONException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
