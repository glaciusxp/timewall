package it.timewall.app.platform.images;

import android.graphics.Bitmap;

/** Applies a bounded, separable box blur to a sampled dashboard background. */
public final class BackgroundBlur {
    public static final String PREFERENCE = "dashboardBackgroundBlur";
    public static final int LEVEL_COUNT = 5;
    static final int DECODE_LIMIT = 512;

    private BackgroundBlur() {}

    public static int levelFromPreference(int saved) {
        // Earlier versions stored the slider index, where index 3 meant 100%.
        switch (saved) {
            case 1:
            case 25:
                return 1;
            case 2:
            case 50:
                return 2;
            case 75:
                return 3;
            case 3:
            case 100:
                return 4;
            default:
                return 0;
        }
    }

    public static int percentage(int level) {
        return level * 25;
    }

    static Bitmap apply(Bitmap image, int level) {
        int width = image.getWidth();
        int height = image.getHeight();
        int strength = percentage(level);
        int radius = Math.max(1, Math.round(Math.max(width, height) * strength / 3200f));
        int[] pixels = new int[width * height];
        int[] line = new int[Math.max(width, height)];
        image.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int y = 0; y < height; y++) {
            blurLine(pixels, line, y * width, 1, width, radius);
        }
        for (int x = 0; x < width; x++) {
            blurLine(pixels, line, x, width, height, radius);
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    private static void blurLine(
            int[] pixels, int[] line, int offset, int stride, int length, int radius) {
        for (int i = 0; i < length; i++) {
            line[i] = pixels[offset + i * stride];
        }
        int alpha = 0, red = 0, green = 0, blue = 0;
        for (int i = -radius; i <= radius; i++) {
            int pixel = line[clamp(i, length)];
            alpha += pixel >>> 24;
            red += (pixel >>> 16) & 255;
            green += (pixel >>> 8) & 255;
            blue += pixel & 255;
        }
        int count = 2 * radius + 1;
        for (int i = 0; i < length; i++) {
            pixels[offset + i * stride] =
                    (alpha / count << 24)
                            | (red / count << 16)
                            | (green / count << 8)
                            | (blue / count);
            int leaving = line[clamp(i - radius, length)];
            int entering = line[clamp(i + radius + 1, length)];
            alpha += (entering >>> 24) - (leaving >>> 24);
            red += ((entering >>> 16) & 255) - ((leaving >>> 16) & 255);
            green += ((entering >>> 8) & 255) - ((leaving >>> 8) & 255);
            blue += (entering & 255) - (leaving & 255);
        }
    }

    private static int clamp(int index, int length) {
        return Math.max(0, Math.min(index, length - 1));
    }
}
