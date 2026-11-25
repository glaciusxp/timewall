package it.timewall.app.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.RemoteViews;

import it.timewall.app.configuration.DashboardFont;

/** Renders bundled fonts for launchers whose RemoteViews cannot load app typefaces. */
final class WidgetText {
    private final DisplayMetrics metrics;
    private final int widthDp;
    private final Typeface normal;
    private final Typeface bold;

    WidgetText(Context context, DashboardFont font, int widthDp) {
        this.metrics = context.getResources().getDisplayMetrics();
        this.widthDp = widthDp;
        normal = loadTypeface(context, font);
        bold = normal == null ? null : Typeface.create(normal, Typeface.BOLD);
    }

    static Typeface loadTypeface(Context context, DashboardFont font) {
        if (font != DashboardFont.SYSTEM_ROBOTO) {
            try {
                return Typeface.createFromAsset(context.getAssets(), font.file);
            } catch (RuntimeException unavailable) {
                // Fall back to native text when a bundled typeface cannot be loaded.
            }
        }
        return null;
    }

    int lineWidthDp(String text, int sizeSp) {
        TextPaint paint = new TextPaint();
        paint.setTypeface(normal);
        paint.setTextSize(sizeSp * metrics.scaledDensity);
        return (int) Math.ceil((paint.measureText(text) + 2) / metrics.density);
    }

    int heightDp(int titleSp, int detailSp, boolean hasStatus) {
        TextPaint paint = new TextPaint();
        paint.setTypeface(bold == null ? Typeface.DEFAULT_BOLD : bold);
        paint.setTextSize(titleSp * metrics.scaledDensity);
        float titleHeight = paint.descent() - paint.ascent();
        paint.setTypeface(normal);
        paint.setTextSize(detailSp * metrics.scaledDensity);
        float detailHeight = paint.descent() - paint.ascent();
        return (int)
                Math.ceil((titleHeight + detailHeight * (hasStatus ? 2 : 1)) / metrics.density);
    }

    void show(
            RemoteViews views,
            int textId,
            int imageId,
            String text,
            int color,
            int sizeSp,
            boolean emphasis,
            int horizontalInsetDp) {
        boolean visible = !text.isEmpty();
        boolean raster = visible && normal != null;
        views.setViewVisibility(textId, visible && !raster ? View.VISIBLE : View.GONE);
        views.setViewVisibility(imageId, raster ? View.VISIBLE : View.GONE);
        if (!visible) return;
        if (!raster) {
            views.setTextViewText(textId, text);
            views.setTextColor(textId, color);
            views.setTextViewTextSize(textId, android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp);
            return;
        }

        int width = Math.max(1, Math.round((widthDp - horizontalInsetDp) * metrics.density));
        TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG | TextPaint.SUBPIXEL_TEXT_FLAG);
        paint.setTypeface(emphasis ? bold : normal);
        paint.setTextSize(sizeSp * metrics.scaledDensity);
        paint.setColor(color);
        String line = text.replace('\n', ' ').replace('\r', ' ');
        CharSequence fitted = TextUtils.ellipsize(line, paint, width, TextUtils.TruncateAt.END);
        int lineWidth =
                Math.max(
                        1,
                        Math.min(
                                width, (int) Math.ceil(Layout.getDesiredWidth(fitted, paint)) + 2));
        StaticLayout layout =
                new StaticLayout(
                        fitted, paint, lineWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0, false);
        Bitmap bitmap =
                Bitmap.createBitmap(
                        lineWidth, Math.max(1, layout.getHeight()), Bitmap.Config.ARGB_8888);
        bitmap.setDensity(metrics.densityDpi);
        layout.draw(new Canvas(bitmap));
        views.setImageViewBitmap(imageId, bitmap);
        views.setContentDescription(imageId, text);
    }
}
