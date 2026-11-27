package it.timewall.app.ui.dashboard;

import static it.timewall.app.domain.time.CalendarDays.midnight;
import static it.timewall.app.domain.time.CalendarDays.nextDay;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import it.timewall.app.BuildConfig;
import it.timewall.app.configuration.DashboardFont;
import it.timewall.app.configuration.DashboardTheme;
import it.timewall.app.configuration.Palette;
import it.timewall.app.configuration.SettingsStore;
import it.timewall.app.data.calendar.CalendarDiskCache;
import it.timewall.app.data.calendar.CalendarFeed;
import it.timewall.app.data.calendar.CalendarRefreshWorker;
import it.timewall.app.data.calendar.CalendarRepository;
import it.timewall.app.data.weather.WeatherForecast;
import it.timewall.app.domain.alerts.EventAlerts;
import it.timewall.app.domain.model.CalendarEvent;
import it.timewall.app.domain.model.Feed;
import it.timewall.app.domain.time.TodayEvents;
import it.timewall.app.localization.DashboardDates;
import it.timewall.app.localization.I18n;
import it.timewall.app.localization.TimeDisplay;
import it.timewall.app.platform.window.BackNavigation;
import it.timewall.app.platform.window.WindowCompatibility;
import it.timewall.app.ui.common.DashboardStyle;
import it.timewall.app.ui.common.ViewFactory;
import it.timewall.app.ui.info.InfoScreen;
import it.timewall.app.ui.settings.CustomThemeEditor;
import it.timewall.app.ui.settings.SettingsScreen;
import it.timewall.app.ui.weather.DashboardWeather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DashboardActivity extends Activity {
    private static final int PICK_BACKGROUND = 101;
    private static final int COLUMN_INSET = 12;
    private static final int NOW_DOT_SIZE = 14;
    private static final int CARD_CORNER_RADIUS = 5;
    private static final int EVENT_CALENDAR_BORDER_PX = 10;
    private static final String FONT_PREFERENCE = "dashboardFont";
    private static final String SHOW_SECONDS_PREFERENCE = "clock.showSeconds";

    private static final long REFRESH_MS = 5 * 60 * 1000L;
    // IDs 0–2 belonged to retired themes. Keep the remaining IDs stable.

    private static final String EXCLUDED_CALENDARS = "excluded_calendars";
    private static final String ICON_SETTINGS = "\uf013";
    private static final String ICON_INFO = "\uf05a";
    private static final String ICON_CHECK = "\uf00c";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    // Survives Activity recreation: deletion must not be dropped by the image worker's shutdown.
    private CalendarRepository calendars;
    private final List<Feed> feeds = new ArrayList<>();
    private final Set<String> excludedCalendars = new HashSet<>();
    private List<CalendarEvent> events = Collections.emptyList();
    private TextView clock, date, status;
    private LinearLayout todayList, upcomingList, featuredList;
    private ScrollView eventsScroll, featuredScroll;
    private DashboardDates dates;
    private Typeface menuIcons;
    private ViewFactory views;
    private DashboardFont selectedFont = DashboardFont.SYSTEM_ROBOTO;
    private Typeface dashboardTypeface;
    private boolean showSeconds;
    private boolean use24Hour;
    private int clockDateFormat;
    private int restoredAgendaScroll = -1;
    private int restoredFeaturedScroll = -1;
    private long displayedDay = Long.MIN_VALUE;
    private long nextChange = Long.MAX_VALUE;
    private long lastTick;
    private boolean resumed;
    private EventAlerts eventAlerts = new EventAlerts();
    private MediaPlayer eventSound;
    private final List<GradientDrawable> eventPulseBorders = new ArrayList<>();
    private Set<String> pulsingEvents = Collections.emptySet();
    private ValueAnimator eventPulse;
    private int revision;
    private boolean darkTheme;
    private boolean automaticCardText;
    private DashboardTheme selectedTheme;
    private Palette palette;
    private int accentColor;
    private Button refreshButton;
    private boolean haveInitialEvents;
    private DashboardWeather weather;
    private SettingsScreen settingsScreen;
    private View infoScreen;
    private BackNavigation backNavigation;
    private FrameLayout dashboardHost;
    private View dashboardContent;

    private final Runnable tick =
            new Runnable() {
                /**
                 * Aggiorna ogni secondo ora e data e verifica le notifiche. Ridisegna gli eventi
                 * quando cambia il giorno, scatta un confine temporale o serve aggiornare il
                 * countdown, poi pianifica il tick successivo.
                 */
                @Override
                public void run() {
                    long now = System.currentTimeMillis();
                    clock.setText(
                            TimeDisplay.time(DashboardActivity.this, now, showSeconds, use24Hour));
                    date.setText(clockDate(now));
                    Set<String> alerts = eventAlerts.tick(events, lastTick, now);
                    showEventAlert(alerts);
                    if (!alerts.isEmpty()
                            || displayedDay != midnight(now)
                            || now >= nextChange
                            || now < lastTick) {
                        render(now, displayedDay != midnight(now));
                    }
                    lastTick = now;
                    handler.postDelayed(this, 1000 - now % 1000);
                }
            };

    private final Runnable refresh = this::refreshFeeds;

    /**
     * Apre esplicitamente le preferenze storiche della dashboard, così il nuovo package
     * dell’Activity non azzera calendari e personalizzazioni.
     *
     * <p>Preserve the preferences file used before the Activity package was separated.
     *
     * @param mode modalità di accesso alle preferenze richiesta dal chiamante
     * @return archivio di preferenze con il nome storico compatibile
     */
    @Override
    public SharedPreferences getPreferences(int mode) {
        return SettingsStore.open(this, mode);
    }

    /**
     * Ripristina lingua, tema, font, configurazione e stato trattenuto; crea repository e layout
     * della dashboard e collega le azioni. Gestisce font non caricabili con ritorno al font di
     * sistema.
     *
     * @param state stato Android salvato; può essere null al primo avvio
     */
    @Override
    protected void onCreate(Bundle state) {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        I18n.apply(this, preferences);
        dates = new DashboardDates(this);
        DashboardTheme defaultTheme = DashboardTheme.SLATE_AZURE_DARK;
        int savedTheme = preferences.getInt("theme", defaultTheme.id);
        selectedTheme = DashboardTheme.fromSavedId(savedTheme);
        int[] customColors =
                selectedTheme == DashboardTheme.CUSTOM
                        ? CustomThemeEditor.loadColors(preferences)
                        : null;
        if (selectedTheme == DashboardTheme.CUSTOM) {
            int[] colors = customColors;
            if (colors != null) {
                int automaticText = ViewFactory.contrastingText(colors[1]);
                automaticCardText = colors.length == CustomThemeEditor.LEGACY_COLOR_COUNT;
                darkTheme = automaticText == Color.WHITE;
                palette = CustomThemeEditor.customPalette(colors);
            } else {
                selectedTheme = defaultTheme;
            }
        }
        if (selectedTheme != DashboardTheme.CUSTOM) {
            darkTheme = selectedTheme.dark;
            palette = selectedTheme.palette;
        }
        accentColor = palette.accent;
        setTheme(
                darkTheme
                        ? android.R.style.Theme_Holo_NoActionBar
                        : android.R.style.Theme_Holo_Light_NoActionBar);
        super.onCreate(state);
        backNavigation =
                new BackNavigation(
                        this,
                        () -> {
                            if (infoScreen != null) closeInfo();
                            else if (settingsScreen != null) settingsScreen.back();
                        });
        showSeconds = preferences.getBoolean(SHOW_SECONDS_PREFERENCE, true);
        use24Hour = preferences.getBoolean(TimeDisplay.HOUR_24, true);
        clockDateFormat = preferences.getInt(TimeDisplay.DATE_FORMAT, 0);
        String fontFile = preferences.getString(FONT_PREFERENCE, "");
        if (!"".equals(fontFile)
                && DashboardFont.fromSavedFile(fontFile) == DashboardFont.SYSTEM_ROBOTO) {
            preferences.edit().remove(FONT_PREFERENCE).apply();
        }
        for (DashboardFont font : DashboardFont.values()) {
            if (!font.file.isEmpty() && font.file.equals(fontFile)) {
                try {
                    dashboardTypeface = Typeface.createFromAsset(getAssets(), font.file);
                    selectedFont = font;
                } catch (RuntimeException exception) {
                    preferences.edit().remove(FONT_PREFERENCE).apply();
                    Toast.makeText(
                                    this,
                                    I18n.text(DashboardActivity.this, "font_failed"),
                                    Toast.LENGTH_LONG)
                            .show();
                    android.util.Log.w(
                            "TimeWall", "Caricamento font fallito: " + font.file, exception);
                }
                break;
            }
        }
        menuIcons = Typeface.createFromAsset(getAssets(), "font-awesome-solid.ttf");
        views = new ViewFactory(this, palette, dashboardTypeface, menuIcons, darkTheme);
        getWindow()
                .addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        calendars = CalendarRefreshWorker.open(getFilesDir());
        loadFeeds();
        pruneCalendarCache();
        excludedCalendars.addAll(
                preferences.getStringSet(EXCLUDED_CALENDARS, Collections.emptySet()));
        normalizeCalendarFilter();
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof RetainedEvents) {
            events = ((RetainedEvents) retained).events;
            eventAlerts = ((RetainedEvents) retained).alerts;
            restoredAgendaScroll = ((RetainedEvents) retained).agendaScroll;
            restoredFeaturedScroll = ((RetainedEvents) retained).featuredScroll;
            haveInitialEvents = true;
        }

        DashboardLayout layout =
                new DashboardLayout(
                        this,
                        preferences,
                        worker,
                        new DashboardStyle(
                                palette,
                                dashboardTypeface,
                                menuIcons,
                                selectedTheme,
                                selectedFont,
                                darkTheme),
                        views,
                        customColors,
                        new DashboardLayout.Actions() {
                            /**
                             * Inoltra il tap sul pulsante di aggiornamento dell’Agenda al
                             * caricamento manuale dei calendari.
                             */
                            @Override
                            public void refresh() {
                                refreshFeeds();
                            }

                            /**
                             * Apre la selezione dei calendari visibili quando viene premuto il
                             * pulsante filtro.
                             */
                            @Override
                            public void filter() {
                                showCalendarFilter();
                            }

                            /**
                             * Apre il menu della dashboard ancorandolo alla vista che ha ricevuto
                             * il tap.
                             *
                             * @param anchor vista a cui ancorare il menu
                             */
                            @Override
                            public void menu(View anchor) {
                                showMenu(anchor);
                            }
                        });
        dashboardHost = layout.dashboardHost;
        dashboardContent = layout.dashboardContent;
        clock = layout.clock;
        date = layout.date;
        status = layout.status;
        featuredList = layout.featuredList;
        todayList = layout.todayList;
        upcomingList = layout.upcomingList;
        featuredScroll = layout.featuredScroll;
        eventsScroll = layout.eventsScroll;
        refreshButton = layout.refreshButton;
        weather = layout.weather;
        WindowCompatibility.apply(this, dashboardHost);
    }

    /**
     * Riattiva la dashboard, allinea il riferimento temporale delle notifiche, avvia il tick e
     * aggiorna calendari e meteo.
     */
    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        lastTick = System.currentTimeMillis();
        eventAlerts.check(events, null, lastTick, lastTick);
        tick.run();
        refreshFeeds();
        if (weather != null) weather.resume();
    }

    /**
     * Ferma tick e refresh pianificati, rilascia l’audio, annulla il lampeggio e sospende il meteo
     * per non aggiornare una schermata non attiva.
     */
    @Override
    protected void onPause() {
        resumed = false;
        releaseEventSound();
        if (eventPulse != null) {
            eventPulse.cancel();
            eventPulse = null;
            pulsingEvents = Collections.emptySet();
            for (GradientDrawable border : eventPulseBorders) {
                border.setAlpha(0);
            }
            eventPulseBorders.clear();
        }
        if (weather != null) weather.pause();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    /**
     * Chiude le impostazioni, invalida il repository dell'Activity e termina i worker locali. Il
     * worker condiviso dei calendari resta disponibile per completare le pulizie già accodate.
     */
    @Override
    protected void onDestroy() {
        if (calendars != null) calendars.dispose();
        if (backNavigation != null) backNavigation.setEnabled(false);
        if (settingsScreen != null) {
            settingsScreen.dispose();
        }
        if (weather != null) weather.destroy();
        worker.shutdownNow();
        super.onDestroy();
    }

    /**
     * Conserva eventi già caricati, deduplicazione delle notifiche e scroll delle due liste
     * attraverso una ricreazione dell’Activity.
     *
     * @return eventi, notifiche e scroll trattenuti, oppure null prima del caricamento iniziale
     */
    @Override
    public Object onRetainNonConfigurationInstance() {
        return haveInitialEvents
                ? new RetainedEvents(
                        events, eventAlerts, eventsScroll.getScrollY(), featuredScroll.getScrollY())
                : null;
    }

    /**
     * Ricostruisce Agenda ed eventi in evidenza usando calendario locale e filtri selezionati.
     * Mantiene le posizioni di scroll, raggruppa i giorni futuri e mostra i messaggi corretti
     * quando mancano eventi.
     *
     * @param now istante civile corrente, in millisecondi
     * @param resetScroll true per riportare le liste all’inizio anziché mantenere lo scroll
     */
    private void render(long now, boolean resetScroll) {
        eventPulseBorders.clear();
        long today = midnight(now);
        long tomorrow = nextDay(today);
        displayedDay = today;
        nextChange = Long.MAX_VALUE;
        int scrollY =
                restoredAgendaScroll >= 0
                        ? restoredAgendaScroll
                        : resetScroll ? 0 : eventsScroll.getScrollY();
        int featuredY =
                restoredFeaturedScroll >= 0
                        ? restoredFeaturedScroll
                        : resetScroll ? 0 : featuredScroll.getScrollY();
        restoredAgendaScroll = -1;
        restoredFeaturedScroll = -1;
        todayList.removeAllViews();
        upcomingList.removeAllViews();
        featuredList.removeAllViews();
        boolean configured = !feeds.isEmpty();
        ((View) todayList.getParent()).setVisibility(configured ? View.VISIBLE : View.GONE);
        ((View) upcomingList.getParent()).setVisibility(configured ? View.VISIBLE : View.GONE);
        status.setTextSize(configured ? 13 : 17);
        status.setPadding(status.getPaddingLeft(), configured ? 0 : views.dp(16), 0, 0);
        if (!configured) {
            status.setText(I18n.text(this, "no_calendars_configured"));
            featuredList.setPadding(0, 0, 0, 0);
            return;
        }
        List<CalendarEvent> current = new ArrayList<>();
        long upcomingDay = Long.MIN_VALUE;
        for (CalendarEvent event : events) {
            if (excludedCalendars.contains(event.feedAddress)) {
                continue;
            }
            if (TodayEvents.intersectsDay(event, today, tomorrow)) {
                current.add(event);
            } else if (event.start >= tomorrow) {
                long day = midnight(event.start);
                if (day != upcomingDay) {
                    upcomingDay = day;
                    String headingText =
                            day == tomorrow
                                    ? dates.localizedDate(day, "tomorrow_date_pattern")
                                    : dates.dateLabel(day);
                    TextView heading = views.label(headingText, 17, false);
                    heading.setPadding(views.dp(8), views.dp(5), views.dp(8), views.dp(5));
                    LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
                    headingParams.topMargin = views.dp(8);
                    headingParams.bottomMargin = views.dp(4);
                    upcomingList.addView(heading, headingParams);
                }
                addEvent(upcomingList, event, false, false, now);
            }
        }
        TodayEvents selection = new TodayEvents(current, now);
        if (current.isEmpty()) {
            if (haveInitialEvents) {
                addDayStatusLabel(todayList, 17, false);
            }
        } else {
            renderToday(current, selection, now);
        }
        if (upcomingList.getChildCount() == 0 && haveInitialEvents) {
            addStatusLabel(upcomingList, 17, false, "no_upcoming");
        }
        boolean showFeaturedStatus =
                featuredList.getChildCount() == 0 && haveInitialEvents && selection.next == null;
        int featuredPadding = showFeaturedStatus ? 0 : views.dp(12);
        featuredList.setPadding(
                0,
                featuredPadding,
                showFeaturedStatus ? 0 : views.dp(COLUMN_INSET),
                featuredPadding);
        if (showFeaturedStatus) {
            addDayStatusLabel(featuredList, 30, !current.isEmpty());
        }
        eventsScroll.post(() -> eventsScroll.scrollTo(0, scrollY));
        featuredScroll.post(() -> featuredScroll.scrollTo(0, featuredY));
    }

    /**
     * Seleziona eventi giornalieri, primo evento attivo e prossimo evento; aggiorna il quadrante
     * sotto l’orologio e inserisce l’indicatore temporale nell’Agenda.
     *
     * @param eventsToday eventi già filtrati alla giornata corrente e ordinati per inizio
     * @param selection selezione degli eventi per il riepilogo e l’Agenda
     * @param now istante civile corrente, in millisecondi
     */
    private void renderToday(List<CalendarEvent> eventsToday, TodayEvents selection, long now) {
        CalendarEvent activeEvent = selection.active;
        CalendarEvent nextEvent = selection.next;
        nextChange = Math.min(nextChange, selection.nextChange);
        for (CalendarEvent event : selection.allDay)
            addEvent(featuredList, event, false, false, now);
        CalendarEvent featuredEvent = selection.featured;
        if (featuredEvent != null) {
            addEvent(
                    featuredList,
                    featuredEvent,
                    activeEvent != null,
                    featuredEvent == nextEvent,
                    now);
        }
        boolean linePending = activeEvent == null;
        for (CalendarEvent event : eventsToday) {
            if (linePending && event == nextEvent) {
                addNowLine();
                linePending = false;
            }
            addEvent(
                    todayList,
                    event,
                    !event.allDay && event.start <= now && now < event.end,
                    event == nextEvent,
                    now);
        }
        if (linePending) {
            addNowLine();
            addDayStatusLabel(todayList, 17, true);
        }
    }

    /**
     * Aggiunge il messaggio di giornata vuota o conclusa con la spaziatura prevista. Nel quadrante
     * in evidenza centra la label e usa una dimensione maggiore.
     *
     * @param list contenitore in cui inserire riquadri o messaggi
     * @param textSize dimensione del messaggio in sp
     * @param complete true per mostrare il messaggio di giornata conclusa anziché vuota
     */
    private void addDayStatusLabel(LinearLayout list, int textSize, boolean complete) {
        addStatusLabel(list, textSize, complete, complete ? "day_complete" : "no_today");
    }

    /**
     * Rende i messaggi vuoti con colore secondario e spaziatura comune; centra soltanto il
     * riepilogo sotto l'orologio e aggiunge la spunta per la giornata conclusa.
     *
     * @param list contenitore del messaggio
     * @param textSize dimensione del testo in sp
     * @param complete true per aggiungere l'icona della giornata conclusa
     * @param messageKey chiave tradotta del messaggio da visualizzare
     */
    private void addStatusLabel(
            LinearLayout list, int textSize, boolean complete, String messageKey) {
        boolean featured = list == featuredList;
        int color = palette.secondaryText;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(
                (featured ? Gravity.CENTER_HORIZONTAL : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        int topSpace = views.dp(8);
        row.setPadding(0, topSpace, 0, featured ? topSpace : topSpace * 2);

        if (complete) {
            TextView icon = views.label(ICON_CHECK, textSize, false);
            icon.setTypeface(menuIcons);
            icon.setTextColor(color);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon);
        }

        TextView message =
                views.label(I18n.text(DashboardActivity.this, messageKey), textSize, false);
        message.setTextColor(color);
        message.setPadding(complete ? views.dp(8) : 0, 0, 0, 0);
        if (featured) {
            message.setGravity(Gravity.CENTER_HORIZONTAL);
        }
        row.addView(message, new LinearLayout.LayoutParams(-2, -2));
        list.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    /**
     * Inserisce nell’Agenda la linea dell’ora corrente con pallino sporgente e margini verticali,
     * utilizzando il colore principale del tema.
     */
    private void addNowLine() {
        FrameLayout marker = new FrameLayout(this);
        int overhang = views.dp(NOW_DOT_SIZE / 2);
        LinearLayout.LayoutParams markerParams =
                new LinearLayout.LayoutParams(-1, views.dp(NOW_DOT_SIZE));
        markerParams.leftMargin = -overhang;
        markerParams.topMargin = views.dp(7);
        markerParams.bottomMargin = views.dp(7);
        todayList.addView(marker, markerParams);

        View line = new View(this);
        line.setBackgroundColor(accentColor);
        FrameLayout.LayoutParams lineParams =
                new FrameLayout.LayoutParams(-1, views.dp(2), Gravity.CENTER_VERTICAL);
        lineParams.leftMargin = overhang;
        marker.addView(line, lineParams);

        View dot = new View(this);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(accentColor);
        dot.setBackground(circle);
        marker.addView(
                dot, new FrameLayout.LayoutParams(views.dp(NOW_DOT_SIZE), views.dp(NOW_DOT_SIZE)));
    }

    /**
     * Crea un fondo rettangolare del colore richiesto con il raggio degli angoli usato dai riquadri
     * della dashboard.
     *
     * @param color colore ARGB da associare al contenuto
     * @return disegno di fondo o indicatore configurato
     */
    private GradientDrawable roundedBackground(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(views.dp(CARD_CORNER_RADIUS));
        return background;
    }

    /**
     * Aggiunge un riquadro cliccabile per l’evento con bordo sinistro del colore del calendario,
     * titolo e orari. Applica evidenza agli eventi attivi o giornalieri, countdown e dimensioni del
     * quadrante, e registra il bordo da animare quando richiesto.
     *
     * @param list contenitore in cui inserire riquadri o messaggi
     * @param event occorrenza da visualizzare o frammento iCal da usare nella fixture
     * @param active true se l’evento è in corso nell’istante corrente
     * @param next true se l’evento è il prossimo della giornata
     * @param now istante civile corrente, in millisecondi
     */
    private void addEvent(
            LinearLayout list, CalendarEvent event, boolean active, boolean next, long now) {
        boolean featured = list == featuredList;
        LinearLayout row = views.column();
        int cardColor = active || (featured && event.allDay) ? palette.highlight : palette.card;
        GradientDrawable card = roundedBackground(cardColor);
        boolean highlighted = active || (event.allDay && (featured || list == todayList));
        card.setStroke(
                views.dp(highlighted && featured ? 2 : 1),
                highlighted ? accentColor : palette.border);
        GradientDrawable calendarBorder = new GradientDrawable();
        calendarBorder.setColor(event.color);
        float radius = Math.min(views.dp(CARD_CORNER_RADIUS), EVENT_CALENDAR_BORDER_PX);
        calendarBorder.setCornerRadii(new float[] {radius, radius, 0, 0, 0, 0, radius, radius});
        GradientDrawable pulseBorder = null;
        if (eventPulse != null
                && eventPulse.isRunning()
                && pulsingEvents.contains(event.occurrenceKey())) {
            pulseBorder = new GradientDrawable();
            pulseBorder.setCornerRadius(views.dp(CARD_CORNER_RADIUS));
            int pulseColor =
                    ViewFactory.contrastingText(cardColor) == Color.WHITE
                            ? 0xfff5f7ff
                            : palette.accent | 0xff000000;
            pulseBorder.setColor(Color.TRANSPARENT);
            pulseBorder.setStroke(views.dp(3), pulseColor);
            pulseBorder.setAlpha((Integer) eventPulse.getAnimatedValue());
            eventPulseBorders.add(pulseBorder);
        }
        GradientDrawable insetBorder = pulseBorder;
        Drawable[] layers =
                insetBorder == null
                        ? new Drawable[] {card, calendarBorder}
                        : new Drawable[] {card, calendarBorder, insetBorder};
        row.setBackground(
                new LayerDrawable(layers) {
                    /**
                     * Ricalcola i limiti del bordo sinistro colorato quando il riquadro cambia
                     * dimensioni, mantenendone lo spessore in pixel.
                     *
                     * @param bounds nuovi limiti in pixel del riquadro da decorare
                     */
                    @Override
                    protected void onBoundsChange(Rect bounds) {
                        super.onBoundsChange(bounds);
                        calendarBorder.setBounds(
                                bounds.left,
                                bounds.top,
                                Math.min(bounds.right, bounds.left + EVENT_CALENDAR_BORDER_PX),
                                bounds.bottom);
                        if (insetBorder != null) {
                            int inset = views.dp(4);
                            insetBorder.setBounds(
                                    bounds.left + EVENT_CALENDAR_BORDER_PX + inset,
                                    bounds.top + inset,
                                    bounds.right - inset,
                                    bounds.bottom - inset);
                        }
                    }
                });
        int horizontalPadding = views.dp(featured ? 14 : 8);
        int verticalPadding = Math.max(5, views.dp(featured ? 10 : 6));
        row.setPadding(
                EVENT_CALENDAR_BORDER_PX + Math.max(20, horizontalPadding),
                verticalPadding,
                Math.max(10, horizontalPadding),
                verticalPadding);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = views.dp(3);
        rowParams.bottomMargin = views.dp(3);
        list.addView(row, rowParams);
        row.setFocusable(true);
        row.setOnClickListener(
                view -> new EventDetailsDialog(this, palette, views, feeds, use24Hour).show(event));
        int detailSize = featured ? DashboardStyle.FEATURED_DETAIL_SP : 15;
        String eventStatus = "";
        if (active || next) {
            long remaining = (active ? event.end : event.start) - now;
            eventStatus =
                    active
                            ? I18n.text(
                                    this, "ends_in", TimeDisplay.countdownDuration(this, remaining))
                            : TimeDisplay.startsIn(this, remaining);
            if (remaining >= 60_000L) {
                nextChange = Math.min(nextChange, now + remaining % 60_000L + 1);
            }
        }
        TextView title =
                views.label(
                        eventTitle(event), featured ? DashboardStyle.FEATURED_TITLE_SP : 17, true);
        String time =
                event.allDay
                        ? I18n.text(DashboardActivity.this, "all_day")
                        : TimeDisplay.time(this, event.start, false, use24Hour)
                                + " - "
                                + TimeDisplay.time(this, event.end, false, use24Hour);
        TextView statusLabel = null;
        if (!eventStatus.isEmpty()) {
            if (featured) {
                statusLabel = views.secondaryLabel(eventStatus, detailSize);
                row.addView(statusLabel);
            } else {
                time += " \u00b7 " + eventStatus;
            }
        }
        row.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView timeLabel = views.secondaryLabel(time, detailSize);
        if (automaticCardText) {
            int cardText = ViewFactory.contrastingText(cardColor);
            title.setTextColor(cardText);
            timeLabel.setTextColor(cardText);
            if (statusLabel != null) {
                statusLabel.setTextColor(cardText);
            }
        }
        row.addView(timeLabel);
    }

    /**
     * Restituisce il titolo dell’evento o la label tradotta per un evento privo di titolo.
     *
     * @param event occorrenza da visualizzare o frammento iCal da usare nella fixture
     * @return titolo dell’evento oppure la label tradotta di titolo assente
     */
    private String eventTitle(CalendarEvent event) {
        return event.title.isEmpty() ? I18n.text(this, "untitled") : event.title;
    }

    /**
     * Annulla il refresh pianificato e avvia sul worker il caricamento cache-prima-rete dei
     * calendari configurati. Assegna una revisione alla richiesta e pianifica il check successivo
     * dopo cinque minuti.
     */
    private void refreshFeeds() {
        handler.removeCallbacks(refresh);
        int requestedRevision = ++revision;
        List<Feed> configured =
                new ArrayList<>(
                        feeds.subList(0, Math.min(feeds.size(), BuildConfig.CALENDAR_LIMIT)));
        refreshButton.setEnabled(false);
        if (configured.isEmpty()) {
            updateCalendarWidgets();
            status.setText("");
            events = Collections.emptyList();
            haveInitialEvents = true;
            render(System.currentTimeMillis(), true);
            return;
        }
        status.setText(I18n.text(DashboardActivity.this, "refreshing_calendars"));
        boolean showCachedEvents = !haveInitialEvents;
        long cacheGeneration = calendars.generation();
        CalendarRefreshWorker.EXECUTOR.execute(
                () ->
                        calendars.refresh(
                                configured,
                                showCachedEvents,
                                new CalendarRepository.Listener() {
                                    /**
                                     * Inoltra lo snapshot locale alla pubblicazione degli eventi
                                     * senza segnalarlo come completamento del download.
                                     *
                                     * @param cached snapshot degli eventi letti dalla cache locale
                                     */
                                    @Override
                                    public void cached(List<CalendarEvent> cached) {
                                        publishEvents(requestedRevision, cached, -1);
                                    }

                                    /**
                                     * Inoltra lo snapshot finale e il numero di fallimenti alla
                                     * pubblicazione, mantenendo la revisione della richiesta
                                     * originale.
                                     *
                                     * @param loaded snapshot degli eventi da pubblicare
                                     * @param failures numero di calendari il cui download è fallito
                                     */
                                    @Override
                                    public void loaded(List<CalendarEvent> loaded, int failures) {
                                        publishEvents(requestedRevision, loaded, failures);
                                    }
                                },
                                cacheGeneration));
        handler.postDelayed(refresh, REFRESH_MS);
    }

    private void updateCalendarWidgets() {
        sendBroadcast(
                new Intent("it.timewall.app.action.UPDATE_WHATS_NEXT")
                        .setClassName(
                                getPackageName(), "it.timewall.app.ui.widget.WhatsNextWidget"));
        sendBroadcast(
                new Intent("it.timewall.app.action.UPDATE_WHATS_NEXT")
                        .setClassName(getPackageName(), "it.timewall.app.ui.widget.AgendaWidget"));
    }

    /**
     * Ordina gli eventi per inizio e pubblica il risultato sul thread UI soltanto se l’Activity
     * esiste e la revisione è ancora corrente. Aggiorna stato di caricamento, notifiche e contenuto
     * visibile.
     *
     * @param requestedRevision revisione a cui appartiene il risultato asincrono
     * @param loaded snapshot degli eventi da pubblicare
     * @param failures fallimenti del download; un valore negativo identifica il risultato della
     *     sola cache
     */
    private void publishEvents(int requestedRevision, List<CalendarEvent> loaded, int failures) {
        Collections.sort(loaded, (first, second) -> Long.compare(first.start, second.start));
        handler.post(
                () -> {
                    if (isDestroyed() || requestedRevision != revision) {
                        return;
                    }
                    if (failures >= 0) {
                        refreshButton.setEnabled(!feeds.isEmpty());
                        status.setText(
                                failures == 0
                                        ? ""
                                        : I18n.text(this, "calendar_failures", failures));
                    }
                    long now = System.currentTimeMillis();
                    if (resumed) {
                        showEventAlert(
                                eventAlerts.check(
                                        loaded,
                                        haveInitialEvents && failures >= 0 ? events : null,
                                        lastTick,
                                        now));
                    }
                    haveInitialEvents = true;
                    events = loaded;
                    if (failures >= 0) updateCalendarWidgets();
                    render(now, false);
                });
    }

    /**
     * Avvia il suono e un lampeggio morbido dei bordi associati alle occorrenze segnalate. Aggiorna
     * i riquadri per raccogliere i bordi da animare.
     *
     * @param alerts chiavi delle occorrenze da notificare e animare
     */
    private void showEventAlert(Set<String> alerts) {
        if (alerts.isEmpty()) {
            return;
        }
        if (eventPulse != null) {
            eventPulse.cancel();
        }
        pulsingEvents = alerts;
        eventPulse = ValueAnimator.ofInt(0, 235);
        eventPulse.setDuration(1000);
        eventPulse.setRepeatMode(ValueAnimator.REVERSE);
        eventPulse.setRepeatCount(3);
        eventPulse.addUpdateListener(
                animation -> {
                    int alpha = (Integer) animation.getAnimatedValue();
                    for (GradientDrawable border : eventPulseBorders) {
                        border.setAlpha(alpha);
                    }
                });
        eventPulse.start();
        playEventSound();
    }

    /**
     * Riproduce la risorsa audio di notifica rilasciando l’eventuale player precedente; registra la
     * pulizia al completamento o all’errore.
     */
    private void playEventSound() {
        if (eventSound != null) {
            return;
        }
        int resource = getResources().getIdentifier("notifica_evento", "raw", getPackageName());
        try {
            eventSound = MediaPlayer.create(this, resource);
            if (eventSound != null) {
                eventSound.setOnCompletionListener(player -> releaseEventSound());
                eventSound.setOnErrorListener(
                        (player, what, extra) -> {
                            releaseEventSound();
                            return true;
                        });
                eventSound.start();
            }
        } catch (RuntimeException exception) {
            releaseEventSound();
            android.util.Log.w("TimeWall", "Riproduzione avviso evento fallita", exception);
        }
    }

    /**
     * Rilascia il MediaPlayer se presente e ne azzera il riferimento, evitando risorse audio
     * trattenute dopo una notifica o una pausa.
     */
    private void releaseEventSound() {
        if (eventSound != null) {
            eventSound.release();
            eventSound = null;
        }
    }

    /**
     * Apre il menu ancorato al pulsante della dashboard con le azioni Impostazioni e Info e i
     * relativi glifi.
     *
     * @param anchor vista a cui ancorare il menu
     */
    private void showMenu(View anchor) {
        String[] labels = {I18n.text(this, "settings"), I18n.text(this, "about")};
        String[] icons = {ICON_SETTINGS, ICON_INFO};
        Runnable[] actions = {this::showSettings, this::showInfo};
        ListPopupWindow menu = new ListPopupWindow(this);
        menu.setAnchorView(anchor);
        menu.setDropDownGravity(Gravity.RIGHT);
        menu.setContentWidth(views.dp(240));
        menu.setModal(true);
        ArrayAdapter<String> entries =
                new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
                    /**
                     * Costruisce la riga del menu con icona e testo localizzato, riusando la vista
                     * quando possibile.
                     *
                     * @param position indice della voce selezionata o della riga da visualizzare
                     * @param convertView vista riciclabile; null se occorre crearne una nuova
                     * @param parent contenitore o selettore che richiede la vista
                     * @return vista costruita o aggiornata, pronta per il contenitore chiamante
                     */
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        LinearLayout row = new LinearLayout(DashboardActivity.this);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(views.dp(12), 0, views.dp(12), 0);
                        row.setMinimumHeight(views.dp(48));

                        TextView icon = views.label(icons[position], 17, false);
                        icon.setTypeface(menuIcons);
                        icon.setGravity(Gravity.CENTER);
                        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                        row.addView(icon, new LinearLayout.LayoutParams(views.dp(28), -2));

                        TextView title = views.label(labels[position], 16, false);
                        title.setPadding(views.dp(10), 0, 0, 0);
                        row.addView(title);
                        return row;
                    }
                };
        menu.setAdapter(entries);
        menu.setOnItemClickListener(
                (parent, view, position, id) -> {
                    menu.dismiss();
                    actions[position].run();
                });
        menu.show();
    }

    /**
     * Queues startup/save cleanup on the shared serial calendar worker; canceled drafts never call
     * it.
     */
    private void pruneCalendarCache() {
        java.io.File directory = getFilesDir();
        Set<String> addresses = new HashSet<>();
        for (Feed feed : feeds) addresses.add(feed.address);
        long generation = CalendarDiskCache.generation();
        CalendarRefreshWorker.EXECUTOR.execute(
                () -> {
                    if (generation != CalendarDiskCache.generation()) return;
                    try {
                        CalendarDiskCache.retainOnly(directory, addresses);
                    } catch (Exception failure) {
                        handler.post(
                                () -> {
                                    if (!isDestroyed()
                                            && generation == CalendarDiskCache.generation())
                                        Toast.makeText(
                                                        this,
                                                        I18n.text(this, "calendar_cache_error"),
                                                        Toast.LENGTH_LONG)
                                                .show();
                                });
                    }
                });
    }

    /**
     * Invalidates old downloads immediately, clears disk/memory serially, then refreshes saved
     * calendars.
     */
    private void clearCalendarCache(Runnable completion) {
        handler.removeCallbacks(refresh);
        revision++;
        long generation = CalendarDiskCache.invalidate();
        java.io.File directory = getFilesDir();
        CalendarRepository repository = calendars;
        events = Collections.emptyList();
        haveInitialEvents = false;
        render(System.currentTimeMillis(), true);
        status.setText(I18n.text(this, "clearing_calendar_cache"));
        CalendarRefreshWorker.EXECUTOR.execute(
                () -> {
                    boolean cleared;
                    repository.clearMemory();
                    try {
                        CalendarDiskCache.clear(directory);
                        cleared = true;
                    } catch (Exception failure) {
                        cleared = false;
                    }
                    boolean success = cleared;
                    handler.post(
                            () -> {
                                completion.run();
                                if (isDestroyed() || generation != CalendarDiskCache.generation())
                                    return;
                                Toast.makeText(
                                                this,
                                                I18n.text(
                                                        this,
                                                        success
                                                                ? "calendar_cache_cleared"
                                                                : "calendar_cache_error"),
                                                Toast.LENGTH_LONG)
                                        .show();
                                updateCalendarWidgets();
                                if (success) refreshFeeds();
                                else {
                                    status.setText(I18n.text(this, "calendar_cache_error"));
                                    refreshButton.setEnabled(!feeds.isEmpty());
                                    handler.postDelayed(refresh, REFRESH_MS);
                                }
                            });
                });
    }

    /**
     * Mostra la schermata impostazioni sopra la dashboard e collega apertura, chiusura e
     * salvataggio. Dopo il salvataggio riallinea calendari, filtri e formato data/ora, ricreando
     * l’Activity soltanto quando serve.
     */
    private void showSettings() {
        if (settingsScreen != null) return;
        settingsScreen =
                new SettingsScreen(
                        this,
                        getPreferences(MODE_PRIVATE),
                        new SettingsScreen.Host() {
                            /**
                             * Performs the immediate cache action independently of the settings
                             * draft.
                             */
                            @Override
                            public void clearCalendarCache(Runnable completion) {
                                DashboardActivity.this.clearCalendarCache(completion);
                            }

                            /**
                             * Nasconde il contenuto della dashboard e aggiunge la schermata
                             * impostazioni al contenitore a tutto schermo.
                             *
                             * @param screen vista della schermata impostazioni da inserire o
                             *     rimuovere
                             */
                            @Override
                            public void opened(View screen) {
                                backNavigation.setEnabled(true);
                                dashboardContent.setVisibility(View.GONE);
                                dashboardHost.addView(screen, new FrameLayout.LayoutParams(-1, -1));
                            }

                            /**
                             * Rimuove la schermata impostazioni, ripristina la dashboard e azzera
                             * il controller della schermata chiusa.
                             *
                             * @param screen vista della schermata impostazioni da inserire o
                             *     rimuovere
                             */
                            @Override
                            public void closed(View screen) {
                                backNavigation.setEnabled(false);
                                dashboardHost.removeView(screen);
                                dashboardContent.setVisibility(View.VISIBLE);
                                settingsScreen = null;
                            }

                            /**
                             * Applica alla dashboard la bozza appena salvata: rimuove eventi di
                             * calendari eliminati, normalizza i filtri e rilegge le opzioni
                             * dell’orologio. Ricrea l’Activity per modifiche di aspetto o lingua,
                             * altrimenti aggiorna le viste e gli eventuali calendari modificati.
                             *
                             * @param pendingFeeds lista dei calendari della bozza non ancora
                             *     salvata
                             * @param refreshActivity true se occorre ricreare l’Activity per
                             *     applicare le scelte salvate
                             */
                            @Override
                            public void saved(List<Feed> pendingFeeds, boolean refreshActivity) {
                                boolean calendarsChanged = !feeds.equals(pendingFeeds);
                                Set<String> previousAddresses = new HashSet<>();
                                for (Feed feed : feeds) previousAddresses.add(feed.address);
                                Set<String> nextAddresses = new HashSet<>();
                                for (Feed feed : pendingFeeds) nextAddresses.add(feed.address);
                                if (!previousAddresses.equals(nextAddresses))
                                    CalendarDiskCache.invalidate();
                                feeds.clear();
                                feeds.addAll(pendingFeeds);
                                if (!previousAddresses.equals(nextAddresses)) pruneCalendarCache();
                                normalizeCalendarFilter();
                                if (calendarsChanged) {
                                    revision++;
                                    Set<String> addresses = new HashSet<>();
                                    for (Feed feed : feeds) addresses.add(feed.address);
                                    List<CalendarEvent> retained = new ArrayList<>();
                                    for (CalendarEvent event : events) {
                                        if (addresses.contains(event.feedAddress))
                                            retained.add(event);
                                    }
                                    events = retained;
                                }
                                getPreferences(MODE_PRIVATE)
                                        .edit()
                                        .putStringSet(
                                                EXCLUDED_CALENDARS,
                                                new HashSet<>(excludedCalendars))
                                        .apply();
                                SharedPreferences prefs = getPreferences(MODE_PRIVATE);
                                use24Hour = prefs.getBoolean(TimeDisplay.HOUR_24, true);
                                clockDateFormat = prefs.getInt(TimeDisplay.DATE_FORMAT, 0);
                                showSeconds = prefs.getBoolean(SHOW_SECONDS_PREFERENCE, true);
                                clock.setText(
                                        TimeDisplay.time(
                                                DashboardActivity.this,
                                                System.currentTimeMillis(),
                                                showSeconds,
                                                use24Hour));
                                date.setText(clockDate(System.currentTimeMillis()));
                                updateCalendarWidgets();
                                WeatherForecast.notifyWidgets(DashboardActivity.this);
                                sendBroadcast(
                                        new Intent("it.timewall.app.action.UPDATE_CLOCK_WIDGET")
                                                .setClassName(
                                                        getPackageName(),
                                                        "it.timewall.app.ui.widget.ClockWidget"));
                                if (refreshActivity) recreate();
                                else {
                                    render(System.currentTimeMillis(), false);
                                    if (calendarsChanged) refreshFeeds();
                                }
                            }
                        },
                        weather,
                        feeds,
                        new DashboardStyle(
                                palette,
                                dashboardTypeface,
                                menuIcons,
                                selectedTheme,
                                selectedFont,
                                darkTheme),
                        worker);
        settingsScreen.show();
    }

    /**
     * Chiude Info o delega Indietro alle impostazioni, che chiedono conferma per modifiche non
     * salvate; altrimenti usa il comportamento standard dell'Activity. Su API 33+ BackNavigation
     * usa il dispatcher nativo; questo callback rimane per la compatibilità con API 19–32.
     */
    @Override
    @SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        if (infoScreen != null) {
            closeInfo();
        } else if (settingsScreen != null && settingsScreen.back()) {
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Gestisce soltanto una scelta valida di immagine dallo Storage Access Framework e la inoltra
     * all’editor dello sfondo, riaprendo le impostazioni se necessario.
     *
     * @param requestCode identificativo dell’operazione Android avviata
     * @param resultCode esito restituito dall’Activity esterna
     * @param data Intent restituito dal file picker; può essere null in caso di annullamento
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_BACKGROUND
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {
            if (settingsScreen == null) {
                showSettings();
            }
            settingsScreen.selectBackground(data.getData());
        }
    }

    /** Opens the read-only information screen using the current Settings presentation. */
    private void showInfo() {
        if (infoScreen != null || settingsScreen != null) return;
        infoScreen =
                new InfoScreen(
                                this,
                                new DashboardStyle(
                                        palette,
                                        dashboardTypeface,
                                        menuIcons,
                                        selectedTheme,
                                        selectedFont,
                                        darkTheme),
                                this::closeInfo)
                        .createView();
        dashboardContent.setVisibility(View.GONE);
        dashboardHost.addView(infoScreen, new FrameLayout.LayoutParams(-1, -1));
        backNavigation.setEnabled(true);
    }

    /** Removes Info and restores dashboard navigation without saving or changing any settings. */
    private void closeInfo() {
        if (infoScreen == null) return;
        dashboardHost.removeView(infoScreen);
        infoScreen = null;
        dashboardContent.setVisibility(View.VISIBLE);
        backNavigation.setEnabled(false);
    }

    /**
     * Mostra il filtro multiselezione dei calendari con i colori identificativi. Al salvataggio
     * aggiorna l’insieme escluso persistito e ridisegna gli eventi.
     */
    private void showCalendarFilter() {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle(I18n.text(DashboardActivity.this, "filter_calendar"));
        if (feeds.isEmpty()) {
            builder.setMessage(I18n.text(DashboardActivity.this, "no_calendars"))
                    .setPositiveButton(I18n.text(DashboardActivity.this, "close"), null)
                    .show();
            return;
        }

        CharSequence[] names = new CharSequence[feeds.size()];
        boolean[] selected = new boolean[feeds.size()];
        Set<String> pendingExclusions = new HashSet<>(excludedCalendars);
        for (int index = 0; index < feeds.size(); index++) {
            Feed feed = feeds.get(index);
            android.text.SpannableString name =
                    new android.text.SpannableString("\ufffc " + feed.name);
            GradientDrawable dot = views.calendarDot(feed.color);
            dot.setBounds(0, 0, views.dp(13), views.dp(13));
            name.setSpan(
                    new android.text.style.ImageSpan(
                            dot, android.text.style.ImageSpan.ALIGN_BASELINE),
                    0,
                    1,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            names[index] = name;
            selected[index] = !pendingExclusions.contains(feed.address);
        }
        builder.setMultiChoiceItems(
                        names,
                        selected,
                        (dialog, index, checked) -> {
                            String address = feeds.get(index).address;
                            if (checked) {
                                pendingExclusions.remove(address);
                            } else {
                                pendingExclusions.add(address);
                            }
                            ((AlertDialog) dialog)
                                    .getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(pendingExclusions.size() < feeds.size());
                        })
                .setNegativeButton(I18n.text(DashboardActivity.this, "cancel"), null)
                .setPositiveButton(
                        I18n.text(DashboardActivity.this, "apply"),
                        (dialog, which) -> {
                            excludedCalendars.clear();
                            excludedCalendars.addAll(pendingExclusions);
                            getPreferences(MODE_PRIVATE)
                                    .edit()
                                    .putStringSet(EXCLUDED_CALENDARS, pendingExclusions)
                                    .apply();
                            render(System.currentTimeMillis(), true);
                        })
                .show();
    }

    /**
     * Elimina dai filtri gli indirizzi che non appartengono più ai calendari configurati,
     * mantenendo le esclusioni ancora pertinenti.
     */
    private void normalizeCalendarFilter() {
        Set<String> addresses = new HashSet<>();
        for (Feed feed : feeds) {
            addresses.add(feed.address);
        }
        excludedCalendars.retainAll(addresses);
        if (excludedCalendars.size() == addresses.size()) {
            excludedCalendars.clear();
        }
    }

    /**
     * Ripristina i calendari dal JSON delle preferenze e conserva solo indirizzi accettati. Se il
     * JSON è illeggibile mostra l’errore di configurazione senza interrompere l’avvio.
     */
    private void loadFeeds() {
        try {
            JSONArray array = new JSONArray(getPreferences(MODE_PRIVATE).getString("feeds", "[]"));
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.getJSONObject(index);
                String address = item.getString("address");
                if (CalendarFeed.accepts(address)) {
                    feeds.add(new Feed(item.getString("name"), address, item.getInt("color")));
                }
            }
        } catch (JSONException exception) {
            Toast.makeText(
                            this,
                            I18n.text(DashboardActivity.this, "calendar_config_error"),
                            Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private final class ColorLabelAdapter extends ArrayAdapter<String> {
        private final int[] colors;
        private final int minimumLeftPadding;

        /**
         * Configura le label del selettore con i colori delle voci e il padding minimo in dp.
         *
         * @param layout risorsa Android della riga del selettore
         * @param names label delle voci del selettore
         * @param colors colori ARGB delle voci o della palette nell’ordine previsto
         * @param minimumLeftPaddingDp padding minimo sinistro espresso in dp
         */
        ColorLabelAdapter(int layout, String[] names, int[] colors, int minimumLeftPaddingDp) {
            super(DashboardActivity.this, layout, names);
            this.colors = colors;
            this.minimumLeftPadding = views.dp(minimumLeftPaddingDp);
        }

        /**
         * Aggiunge il pallino del calendario alla riga selezionata del filtro, mantenendo il
         * padding minimo previsto.
         *
         * @param position indice della voce selezionata o della riga da visualizzare
         * @param convertView vista riciclabile; null se occorre crearne una nuova
         * @param parent contenitore o selettore che richiede la vista
         * @return vista costruita o aggiornata, pronta per il contenitore chiamante
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return withDot((TextView) super.getView(position, convertView, parent), position);
        }

        /**
         * Applica il pallino colorato anche alle righe dell’elenco aperto del selettore.
         *
         * @param position indice della voce selezionata o della riga da visualizzare
         * @param convertView vista riciclabile; null se occorre crearne una nuova
         * @param parent contenitore o selettore che richiede la vista
         * @return vista costruita o aggiornata, pronta per il contenitore chiamante
         */
        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return withDot(
                    (TextView) super.getDropDownView(position, convertView, parent), position);
        }

        /**
         * Configura il pallino corrispondente alla posizione e aumenta il padding sinistro soltanto
         * se inferiore al minimo richiesto.
         *
         * @param text testo da visualizzare o analizzare
         * @param position indice della voce selezionata o della riga da visualizzare
         * @return label configurata secondo lo stile descritto
         */
        private TextView withDot(TextView text, int position) {
            views.setCalendarDot(text, colors[position], 13);
            if (text.getPaddingLeft() < minimumLeftPadding) {
                text.setPadding(
                        minimumLeftPadding,
                        text.getPaddingTop(),
                        text.getPaddingRight(),
                        text.getPaddingBottom());
            }
            return text;
        }
    }

    /**
     * Formatta la data dell’orologio con la scelta salvata, mantenendo il formato esteso tradotto
     * quando è selezionata l’opzione predefinita.
     *
     * @param time istante civile da formattare o delimitare, in millisecondi
     * @return data corrente nel formato scelto nelle impostazioni
     */
    private String clockDate(long time) {
        if (clockDateFormat == 0) {
            return dates.localizedDate(time, "clock_date_pattern");
        }
        return dates.format(time, TimeDisplay.datePattern(this, clockDateFormat));
    }

    private static final class RetainedEvents {
        public final List<CalendarEvent> events;
        public final EventAlerts alerts;
        public final int agendaScroll, featuredScroll;

        /**
         * Raggruppa eventi, stato delle notifiche e scroll da conservare durante la ricreazione
         * dell’Activity.
         *
         * @param events occorrenze da elaborare o pubblicare; nella lettura iCal è la lista di
         *     destinazione dei blocchi
         * @param alerts chiavi delle occorrenze da notificare e animare
         * @param agendaScroll posizione verticale da ripristinare nell’Agenda
         * @param featuredScroll posizione verticale del quadrante sotto l’orologio
         */
        RetainedEvents(
                List<CalendarEvent> events,
                EventAlerts alerts,
                int agendaScroll,
                int featuredScroll) {
            this.events = events;
            this.alerts = alerts;
            this.agendaScroll = agendaScroll;
            this.featuredScroll = featuredScroll;
        }
    }
}
