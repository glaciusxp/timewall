package it.timewall.app.ui.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import it.timewall.app.R;
import it.timewall.app.configuration.DashboardFont;
import it.timewall.app.configuration.Palette;
import it.timewall.app.configuration.SettingsStore;
import it.timewall.app.data.weather.WeatherForecast;
import it.timewall.app.domain.model.Feed;
import it.timewall.app.localization.DashboardDates;
import it.timewall.app.localization.I18n;
import it.timewall.app.localization.TimeDisplay;
import it.timewall.app.ui.common.ViewFactory;

import java.text.NumberFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Edits the calendar filter belonging to a single widget instance. */
public final class WhatsNextConfig extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        SharedPreferences dashboard = SettingsStore.open(this, MODE_PRIVATE);
        I18n.apply(this, dashboard);
        WidgetTheme theme = new WidgetTheme(dashboard);
        setTheme(
                theme.dark
                        ? android.R.style.Theme_Holo_NoActionBar
                        : android.R.style.Theme_Holo_Light_NoActionBar);
        super.onCreate(state);
        int id =
                getIntent()
                        .getIntExtra(
                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                AppWidgetManager.INVALID_APPWIDGET_ID);
        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        setResult(RESULT_CANCELED, result);
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        boolean clock =
                info != null && new ComponentName(this, ClockWidget.class).equals(info.provider);
        boolean weather =
                info != null && new ComponentName(this, WeatherWidget.class).equals(info.provider);
        boolean whatsNext =
                info != null
                        && new ComponentName(this, WhatsNextWidget.class).equals(info.provider);
        if (info == null
                || (!clock
                        && !weather
                        && !whatsNext
                        && !new ComponentName(this, AgendaWidget.class).equals(info.provider))) {
            finish();
            return;
        }

        Palette palette = theme.palette;
        DashboardFont dashboardFont =
                DashboardFont.fromSavedFile(dashboard.getString("dashboardFont", ""));
        Typeface typeface =
                dashboardFont.file.isEmpty()
                        ? null
                        : Typeface.createFromAsset(getAssets(), dashboardFont.file);
        Typeface icons = Typeface.createFromAsset(getAssets(), "font-awesome-solid.ttf");
        ViewFactory views = new ViewFactory(this, palette, typeface, icons, theme.dark);

        LinearLayout screen = views.column();
        screen.setBackgroundColor(palette.background | Color.BLACK);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(views.dp(8), 0, views.dp(8), 0);
        int headerColor = palette.accent | Color.BLACK;
        int headerText = ViewFactory.contrastingText(headerColor);
        header.setBackgroundColor(headerColor);
        screen.addView(header, new LinearLayout.LayoutParams(-1, views.dp(64)));
        Button back =
                views.addHeaderButton(header, "\uf053", getString(R.string.back), view -> finish());
        back.setBackground(null);
        back.setTextColor(headerText);
        TextView heading = views.label(getString(R.string.widget_settings_title), 24, true);
        heading.setTextColor(headerText);
        heading.setGravity(Gravity.CENTER);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button save = new Button(this);
        save.setText(R.string.save);
        save.setTextColor(headerText);
        save.setBackground(null);
        header.addView(save, new LinearLayout.LayoutParams(-2, -1));
        List<Feed> feeds =
                clock || weather
                        ? java.util.Collections.emptyList()
                        : WhatsNextWidget.feeds(dashboard);
        SharedPreferences widgets = WhatsNextWidget.preferences(this);
        Set<String> saved = widgets.getStringSet(Integer.toString(id), null);
        CheckBox[] choices = new CheckBox[feeds.size()];

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = views.column();
        int inset = views.dp(24);
        form.setPadding(inset, views.dp(16), inset, inset);
        scroll.addView(form);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (!clock && !weather) {
            views.addSettingHeading(form, getString(R.string.calendars), true);
            TextView hint =
                    views.secondaryLabel(
                            getString(
                                    feeds.isEmpty()
                                            ? R.string.no_calendars
                                            : R.string.widget_choose_calendars),
                            15);
            form.addView(hint);
            for (int index = 0; index < feeds.size(); index++) {
                Feed feed = feeds.get(index);
                CheckBox choice = new CheckBox(this);
                views.styleSettingInput(choice);
                choice.setText(feed.name);
                choice.setChecked(saved == null || saved.contains(feed.address));
                choices[index] = choice;
                form.addView(choice);
            }
        }
        TextView transparencyLabel = views.settingHeading("");
        views.addSettingView(form, transparencyLabel, clock || weather, 6);
        SeekBar transparency = new SeekBar(this);
        transparency.setMax(4);
        transparency.setProgress(WhatsNextWidget.transparency(widgets, id));
        form.addView(transparency);
        updateTransparencyLabel(transparencyLabel, transparency);
        transparency.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                        updateTransparencyLabel(transparencyLabel, bar);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {}
                });
        views.addSettingHeading(form, getString(R.string.font), false);
        DashboardFont[] fonts = DashboardFont.values();
        String[] names = new String[fonts.length];
        for (int index = 0; index < fonts.length; index++) {
            names[index] =
                    fonts[index] == DashboardFont.SYSTEM_ROBOTO
                            ? I18n.text(this, fonts[index].label)
                            : fonts[index].label;
        }
        ArrayAdapter<String> fontChoices = views.settingChoices(names);
        Spinner fontPicker = new Spinner(this);
        fontPicker.setAdapter(fontChoices);
        fontPicker.setSelection(WhatsNextWidget.selectedFont(widgets, dashboard, id).ordinal());
        form.addView(fontPicker);

        CheckBox enlargeText = new CheckBox(this);
        if (whatsNext) {
            views.styleSettingInput(enlargeText);
            enlargeText.setText(R.string.widget_enlarge_text);
            enlargeText.setChecked(WhatsNextWidget.enlargedText(widgets, id));
            views.addSettingView(form, enlargeText, false, 0);
        }

        CheckBox seconds = new CheckBox(this);
        Spinner timeFormat = new Spinner(this);
        Spinner dateFormat = new Spinner(this);
        if (clock) {
            views.styleSettingInput(seconds);
            seconds.setText(R.string.show_seconds);
            seconds.setChecked(ClockWidget.showSeconds(widgets, dashboard, id));
            views.addSettingView(form, seconds, false, 0);

            views.addSettingHeading(form, getString(R.string.time_format), false);
            timeFormat.setAdapter(
                    views.settingChoices(getString(R.string.hour_24), getString(R.string.hour_12)));
            timeFormat.setSelection(ClockWidget.use24Hour(widgets, dashboard, id) ? 0 : 1);
            form.addView(timeFormat);

            views.addSettingHeading(form, getString(R.string.date_format), false);
            DashboardDates dates = new DashboardDates(this);
            long now = System.currentTimeMillis();
            String[] previews = new String[4];
            previews[0] = dates.localizedDate(now, "clock_date_pattern");
            for (int index = 1; index < previews.length; index++) {
                previews[index] = dates.format(now, TimeDisplay.datePattern(this, index));
            }
            dateFormat.setAdapter(views.settingChoices(previews));
            dateFormat.setSelection(ClockWidget.dateFormat(widgets, dashboard, id));
            form.addView(dateFormat);
        }

        save.setOnClickListener(
                view -> {
                    SharedPreferences.Editor edit =
                            widgets.edit()
                                    .putInt(
                                            WhatsNextWidget.transparencyKey(id),
                                            transparency.getProgress())
                                    .putString(
                                            WhatsNextWidget.fontKey(id),
                                            fonts[fontPicker.getSelectedItemPosition()].file);
                    if (clock) {
                        edit.putBoolean(ClockWidget.secondsKey(id), seconds.isChecked())
                                .putBoolean(
                                        ClockWidget.hourKey(id),
                                        timeFormat.getSelectedItemPosition() == 0)
                                .putInt(
                                        ClockWidget.dateKey(id),
                                        dateFormat.getSelectedItemPosition())
                                .apply();
                        ClockWidget.requestUpdate(this);
                    } else if (weather) {
                        edit.apply();
                        WeatherForecast.notifyWidgets(this);
                    } else {
                        Set<String> selection = new HashSet<>();
                        for (int index = 0; index < choices.length; index++) {
                            if (choices[index].isChecked()) selection.add(feeds.get(index).address);
                        }
                        edit.putStringSet(Integer.toString(id), selection);
                        if (whatsNext) {
                            edit.putBoolean(
                                    WhatsNextWidget.enlargedTextKey(id), enlargeText.isChecked());
                        }
                        edit.apply();
                        WhatsNextWidget.requestUpdate(this);
                    }
                    setResult(RESULT_OK, result);
                    finish();
                });
        setContentView(screen);
    }

    private void updateTransparencyLabel(TextView label, SeekBar bar) {
        String percent =
                NumberFormat.getPercentInstance(I18n.locale(this)).format(bar.getProgress() / 4.0);
        String text = getString(R.string.widget_background_transparency, percent);
        label.setText(text);
        bar.setContentDescription(text);
    }
}
