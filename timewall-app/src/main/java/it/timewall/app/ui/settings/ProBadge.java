package it.timewall.app.ui.settings;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import it.timewall.app.localization.I18n;
import it.timewall.app.ui.common.ViewFactory;

/** Preview of unavailable settings in the Play edition. */
final class ProBadge {
    private ProBadge() {}

    static LinearLayout row(Activity activity, ViewFactory views, View label) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label);
        add(activity, views, row);
        return row;
    }

    static void add(Activity activity, ViewFactory views, LinearLayout row) {
        ImageView badge = new ImageView(activity);
        badge.setImageResource(resource(activity, views));
        badge.setContentDescription(I18n.text(activity, "pro_future"));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(views.dp(69), views.dp(27));
        size.leftMargin = views.dp(8);
        row.addView(badge, size);
    }

    static void markChoice(Activity activity, ViewFactory views, TextView item) {
        Drawable badge = activity.getResources().getDrawable(resource(activity, views));
        badge.setBounds(0, 0, views.dp(69), views.dp(27));
        item.setCompoundDrawablePadding(views.dp(8));
        item.setCompoundDrawablesRelative(null, null, badge, null);
    }

    private static int resource(Activity activity, ViewFactory views) {
        String name = views.isDarkTheme() ? "tw_pro_dark" : "tw_pro_light";
        return activity.getResources().getIdentifier(name, "drawable", activity.getPackageName());
    }

    static View weatherFields(Activity activity, ViewFactory views) {
        LinearLayout form = views.column();
        form.addView(views.secondaryLabel(I18n.text(activity, "pro_future"), 16));
        views.addSettingHeading(form, I18n.text(activity, "weather_enable"), false);
        CheckBox toggle = new CheckBox(activity);
        views.styleSettingInput(toggle);
        toggle.setText(I18n.text(activity, "enable_show"));
        toggle.setEnabled(false);
        form.addView(toggle);

        views.addSettingHeading(form, I18n.text(activity, "place"), false);
        EditText query = new EditText(activity);
        views.styleSettingInput(query);
        query.setSingleLine(true);
        query.setHint(I18n.text(activity, "search_place"));
        query.setEnabled(false);
        form.addView(query);

        addChoices(activity, views, form, "temperature_unit", "celsius", "fahrenheit");
        addChoices(
                activity,
                views,
                form,
                "refresh_frequency",
                "frequency_15",
                "frequency_30",
                "frequency_60",
                "frequency_120");
        return form;
    }

    private static void addChoices(
            Activity activity,
            ViewFactory views,
            LinearLayout form,
            String heading,
            String... keys) {
        views.addSettingHeading(form, I18n.text(activity, heading), false);
        String[] values = new String[keys.length];
        for (int index = 0; index < keys.length; index++) {
            values[index] = I18n.text(activity, keys[index]);
        }
        Spinner picker = new Spinner(activity);
        picker.setAdapter(views.settingChoices(values));
        picker.setEnabled(false);
        form.addView(picker);
    }
}
