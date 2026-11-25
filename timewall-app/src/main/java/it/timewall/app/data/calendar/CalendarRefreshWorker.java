package it.timewall.app.data.calendar;

import android.os.SystemClock;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serializes calendar refreshes and disk maintenance across the dashboard and home widgets. */
public final class CalendarRefreshWorker {
    public static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Map<String, Download> recentDownloads = new HashMap<>();
    private static long downloadGeneration = -1;

    private CalendarRefreshWorker() {}

    public static CalendarRepository open(File directory, long requestedAt) {
        return new CalendarRepository(
                address -> download(directory, address, requestedAt),
                new CalendarRepository.Store() {
                    @Override
                    public long generation() {
                        return CalendarDiskCache.generation();
                    }

                    @Override
                    public byte[] read(String address) throws Exception {
                        return CalendarDiskCache.read(directory, address);
                    }

                    @Override
                    public void write(String address, byte[] body, long generation)
                            throws Exception {
                        CalendarDiskCache.write(directory, address, body, generation);
                    }
                });
    }

    public static CalendarRepository open(File directory) {
        return open(directory, Long.MAX_VALUE);
    }

    private static byte[] download(File directory, String address, long requestedAt)
            throws Exception {
        long generation = CalendarDiskCache.generation();
        if (downloadGeneration != generation) {
            recentDownloads.clear();
            downloadGeneration = generation;
        }
        Download recent = recentDownloads.get(address);
        if (recent != null && recent.completedAt >= requestedAt) {
            try {
                byte[] cached = CalendarDiskCache.read(directory, address);
                if (cached != null && Arrays.equals(recent.hash, CalendarFeed.hash(cached))) {
                    return cached;
                }
            } catch (Exception unavailable) {
                // Retry through the usual network path when no usable disk entry exists.
            }
        }
        byte[] body = CalendarFeed.download(address);
        recentDownloads.put(
                address, new Download(SystemClock.elapsedRealtime(), CalendarFeed.hash(body)));
        return body;
    }

    private static final class Download {
        final long completedAt;
        final byte[] hash;

        Download(long completedAt, byte[] hash) {
            this.completedAt = completedAt;
            this.hash = hash;
        }
    }
}
