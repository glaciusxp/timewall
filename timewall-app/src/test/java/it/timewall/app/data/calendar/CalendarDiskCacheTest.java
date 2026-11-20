package it.timewall.app.data.calendar;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/** Exercises deletion on real temporary files without mocking Android's AtomicFile writes. */
public class CalendarDiskCacheTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private static final String ACTIVE =
            "calendar-a5b2ae49959f34a7bad9138ea5742f1505aacd5eae8ec467a8ee0939c654a4f9";
    private static final String REMOVED =
            "calendar-7351e2e1e7411d4177b6f6147717f350611358bd730891997741573b70b84135";

    /**
     * Retains active URL files while removing an old URL and every supported AtomicFile sidecar.
     */
    @Test
    public void removedOrChangedUrlsLoseAllCopies() throws Exception {
        for (String suffix : new String[] {"", ".bak", ".new"}) {
            directory.newFile(ACTIVE + suffix);
            directory.newFile(REMOVED + suffix);
        }
        CalendarDiskCache.retainOnly(
                directory.getRoot(), Collections.singleton("https://example.org/a.ics"));
        for (String suffix : new String[] {"", ".bak", ".new"}) {
            assertTrue(new File(directory.getRoot(), ACTIVE + suffix).exists());
            assertFalse(new File(directory.getRoot(), REMOVED + suffix).exists());
        }
    }

    /**
     * Clears only the exact calendar namespace, preserving unrelated files and never traversing
     * directories.
     */
    @Test
    public void clearPreservesOtherAppFiles() throws Exception {
        directory.newFile(ACTIVE);
        directory.newFile(ACTIVE + ".bak");
        directory.newFile(REMOVED + ".new");
        File background = directory.newFile("background.jpg");
        File other = directory.newFile("calendar-not-a-cache");
        File folder = directory.newFolder(REMOVED);
        File child = new File(folder, "keep.txt");
        assertTrue(child.createNewFile());
        CalendarDiskCache.clear(directory.getRoot());
        assertFalse(new File(directory.getRoot(), ACTIVE).exists());
        assertFalse(new File(directory.getRoot(), ACTIVE + ".bak").exists());
        assertFalse(new File(directory.getRoot(), REMOVED + ".new").exists());
        assertTrue(background.exists());
        assertTrue(other.exists());
        assertTrue(child.exists());
    }

    /** A stale download must return before opening or recreating an AtomicFile. */
    @Test
    public void invalidatedWriteCannotRecreateCache() throws Exception {
        long ticket = CalendarDiskCache.generation();
        CalendarDiskCache.invalidate();
        CalendarDiskCache.write(
                directory.getRoot(), "https://example.org/a.ics", new byte[] {1}, ticket);
        assertEquals(0, directory.getRoot().list().length);
    }

    /**
     * Treats an unavailable cache directory as an error rather than reporting successful deletion.
     */
    @Test(expected = IOException.class)
    public void unavailableDirectoryReportsFailure() throws Exception {
        CalendarDiskCache.clear(directory.newFile("not-a-directory"));
    }
}
