package it.timewall.app.platform.images;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.IOException;
import java.io.InputStream;

/** SAF metadata and sampled decoding; no broad storage permission is needed. */
public final class ImageDocuments {
    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private ImageDocuments() {}

    /**
     * Verifica le dimensioni dell’immagine attraverso il provider documenti e la decodifica con
     * campionamento per contenere la memoria. Restituisce null per contenuti non leggibili o non
     * decodificabili.
     *
     * @param activity Activity proprietaria delle viste e dei servizi Android
     * @param uri URI dell’immagine esposto dal provider documenti
     * @return bitmap decodificata, oppure null quando l’immagine non è leggibile
     */
    public static Bitmap readBackground(Activity activity, Uri uri) {
        return decode(activity, uri, 2048);
    }

    public static Bitmap readBackground(Activity activity, Uri uri, int blurLevel) {
        int level = BackgroundBlur.levelFromPreference(blurLevel);
        if (level == 0) return readBackground(activity, uri);

        Bitmap sampled = decode(activity, uri, BackgroundBlur.DECODE_LIMIT);
        if (sampled == null) return null;
        try {
            Bitmap blurred = BackgroundBlur.apply(sampled, level);
            sampled.recycle();
            return blurred;
        } catch (RuntimeException exception) {
            return sampled;
        }
    }

    private static Bitmap decode(Activity activity, Uri uri, int longestSideLimit) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, options);
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null;
            }
            int longestSide = Math.max(options.outWidth, options.outHeight);
            options.inSampleSize = 1;
            while (longestSide / options.inSampleSize > longestSideLimit) {
                options.inSampleSize *= 2;
            }
            options.inJustDecodeBounds = false;
            try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    /**
     * Legge DISPLAY_NAME dal provider documenti e chiude il cursore. Restituisce una stringa vuota
     * quando il nome non è esposto o il provider non è più accessibile.
     *
     * @param activity Activity proprietaria delle viste e dei servizi Android
     * @param uri URI dell’immagine esposto dal provider documenti
     * @return nome esposto dal provider, oppure stringa vuota quando non disponibile
     */
    public static String displayName(Activity activity, Uri uri) {
        try (Cursor cursor =
                activity.getContentResolver()
                        .query(
                                uri,
                                new String[] {OpenableColumns.DISPLAY_NAME},
                                null,
                                null,
                                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0 && !cursor.isNull(column)) {
                    return cursor.getString(column);
                }
            }
        } catch (RuntimeException ignored) {
            // The provider may no longer expose metadata for a saved image.
        }
        return "";
    }
}
