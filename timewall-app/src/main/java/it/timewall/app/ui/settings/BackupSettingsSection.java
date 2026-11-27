package it.timewall.app.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import it.timewall.app.configuration.SettingsBackup;
import it.timewall.app.data.weather.WeatherForecast;
import it.timewall.app.localization.I18n;
import it.timewall.app.platform.images.ImageDocuments;
import it.timewall.app.ui.common.ViewFactory;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

final class BackupSettingsSection {
    static final int EXPORT = 102;
    static final int IMPORT = 103;
    private static final int MAX_IMAGE = 4 * 1024 * 1024;
    private static final int MAX_DOCUMENT = 6 * 1024 * 1024;
    private static final String PRIVATE_PREFIX = "restored-background-";
    private final Activity activity;
    private final SharedPreferences preferences;
    private final Executor worker;
    private final Runnable restored;
    private final Button export;
    private final Button restore;
    private boolean disposed;

    BackupSettingsSection(
            Activity activity,
            SharedPreferences preferences,
            Executor worker,
            Runnable restored,
            ViewFactory views,
            LinearLayout section) {
        this.activity = activity;
        this.preferences = preferences;
        this.worker = worker;
        this.restored = restored;
        views.addSettingHeading(section, text("backup_title"), false);
        views.addSettingView(section, views.secondaryLabel(text("backup_hint"), 16), false, 12);
        export = new Button(activity);
        export.setText(text("backup_export"));
        restore = new Button(activity);
        restore.setText(text("backup_import"));
        export.setOnClickListener(view -> pick(EXPORT));
        restore.setOnClickListener(view -> pick(IMPORT));
        views.addSettingView(section, export, false, 12);
        views.addSettingView(section, restore, false, 12);
    }

    void dispose() {
        disposed = true;
    }

    private String text(String key) {
        return I18n.text(activity, key);
    }

    private void pick(int request) {
        boolean save = request == EXPORT;
        Intent intent =
                new Intent(save ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(save ? "application/json" : "*/*")
                        .putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        if (save) intent.putExtra(Intent.EXTRA_TITLE, "TimeWall-settings.json");
        try {
            activity.startActivityForResult(intent, request);
        } catch (ActivityNotFoundException failure) {
            message("file_picker_unavailable");
        }
    }

    void selected(int request, Uri uri) {
        busy(true);
        Map<String, ?> snapshot = preferences.getAll();
        worker.execute(
                () -> {
                    if (request == EXPORT) export(uri, snapshot);
                    else load(uri);
                });
    }

    private void export(Uri uri, Map<String, ?> snapshot) {
        try {
            String image = "";
            Object source = snapshot.get(SettingsBackup.IMAGE_URI);
            if (source instanceof String && !((String) source).isEmpty()) {
                image =
                        Base64.encodeToString(
                                exportImage(Uri.parse((String) source)), Base64.NO_WRAP);
            }
            byte[] document =
                    SettingsBackup.encode(snapshot, image).getBytes(StandardCharsets.UTF_8);
            if (document.length > MAX_DOCUMENT) throw new IOException("Backup too large");
            try (OutputStream output = activity.getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IOException("Document unavailable");
                output.write(document);
            }
            finish("backup_exported");
        } catch (IOException | JSONException | RuntimeException failure) {
            finish("backup_error");
        }
    }

    private byte[] exportImage(Uri source) throws IOException {
        try (InputStream input = activity.getContentResolver().openInputStream(source)) {
            byte[] original = read(input, MAX_IMAGE);
            if (isPortableImage(original)) return original;
        } catch (IOException ignored) {
            // A large image can still be exported at dashboard resolution.
        }
        Bitmap image = ImageDocuments.readBackground(activity, source);
        if (image == null) throw new IOException("Background unavailable");
        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            if (!image.compress(Bitmap.CompressFormat.JPEG, 90, result)
                    || result.size() > MAX_IMAGE) {
                throw new IOException("Background too large");
            }
            return result.toByteArray();
        } finally {
            image.recycle();
        }
    }

    private void load(Uri uri) {
        try {
            byte[] bytes;
            try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                bytes = read(input, MAX_DOCUMENT);
            }
            SettingsBackup backup =
                    SettingsBackup.decode(
                            StandardCharsets.UTF_8
                                    .newDecoder()
                                    .decode(ByteBuffer.wrap(bytes))
                                    .toString());
            byte[] image =
                    backup.image.isEmpty() ? null : Base64.decode(backup.image, Base64.DEFAULT);
            if (image != null && !isPortableImage(image))
                throw new IOException("Invalid background");
            activity.runOnUiThread(() -> confirm(backup, image));
        } catch (SettingsBackup.UnsupportedVersion newer) {
            finish("backup_newer");
        } catch (IOException | JSONException | RuntimeException failure) {
            finish("backup_invalid");
        }
    }

    private void confirm(SettingsBackup backup, byte[] image) {
        if (disposed || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
                .setTitle(text("backup_import"))
                .setMessage(text("backup_confirm"))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> busy(false))
                .setOnCancelListener(dialog -> busy(false))
                .setPositiveButton(
                        android.R.string.ok,
                        (dialog, which) -> worker.execute(() -> stage(backup, image)))
                .show();
    }

    private void stage(SettingsBackup backup, byte[] image) {
        File file = null;
        try {
            if (image != null) {
                file = File.createTempFile(PRIVATE_PREFIX, ".image", activity.getFilesDir());
                try (OutputStream output = new FileOutputStream(file)) {
                    output.write(image);
                }
            }
            File staged = file;
            activity.runOnUiThread(() -> apply(backup, staged));
        } catch (IOException | RuntimeException failure) {
            if (file != null) file.delete();
            finish("backup_error");
        }
    }

    private void apply(SettingsBackup backup, File staged) {
        if (disposed || activity.isDestroyed()) {
            if (staged != null) staged.delete();
            return;
        }
        Map<String, ?> previous = preferences.getAll();
        Map<String, Object> settings = new LinkedHashMap<>(backup.settings);
        if (staged != null) settings.put(SettingsBackup.IMAGE_URI, Uri.fromFile(staged).toString());
        if (!editor(settings).commit()) {
            // A failed commit can still update the in-memory preferences.
            editor(previous).commit();
            if (staged != null) staged.delete();
            finish("backup_error");
            return;
        }
        deletePreviousImage(previous.get(SettingsBackup.IMAGE_URI));
        message("backup_restored");
        restored.run();
    }

    private SharedPreferences.Editor editor(Map<String, ?> settings) {
        SharedPreferences.Editor edit = preferences.edit();
        Set<String> keys = SettingsBackup.managedKeys();
        keys.add(WeatherForecast.CACHE_KEY);
        for (String key : keys) {
            edit.remove(key);
            Object value = settings.get(key);
            if (value instanceof String) edit.putString(key, (String) value);
            else if (value instanceof Integer) edit.putInt(key, (int) value);
            else if (value instanceof Long) edit.putLong(key, (long) value);
            else if (value instanceof Boolean) edit.putBoolean(key, (boolean) value);
            else if (value instanceof Set) edit.putStringSet(key, stringSet(value));
        }
        return edit;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> stringSet(Object value) {
        return (Set<String>) value;
    }

    private void deletePreviousImage(Object previous) {
        if (!(previous instanceof String)) return;
        Uri uri = Uri.parse((String) previous);
        if (!"file".equals(uri.getScheme()) || uri.getPath() == null) return;
        File old = new File(uri.getPath());
        if (activity.getFilesDir().equals(old.getParentFile())
                && old.getName().startsWith(PRIVATE_PREFIX)) {
            old.delete();
        }
    }

    private static boolean isPortableImage(byte[] image) {
        if (image.length > MAX_IMAGE) return false;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image, 0, image.length, bounds);
        return bounds.outWidth > 0
                && bounds.outWidth <= 2048
                && bounds.outHeight > 0
                && bounds.outHeight <= 2048;
    }

    private static byte[] read(InputStream input, int limit) throws IOException {
        if (input == null) throw new IOException("Document unavailable");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int received;
        while ((received = input.read(buffer)) != -1) {
            if (bytes.size() + received > limit) throw new IOException("Document too large");
            bytes.write(buffer, 0, received);
        }
        return bytes.toByteArray();
    }

    private void finish(String key) {
        activity.runOnUiThread(
                () -> {
                    if (disposed || activity.isDestroyed()) return;
                    busy(false);
                    message(key);
                });
    }

    private void busy(boolean busy) {
        export.setEnabled(!busy);
        restore.setEnabled(!busy);
    }

    private void message(String key) {
        Toast.makeText(activity, text(key), Toast.LENGTH_LONG).show();
    }
}
