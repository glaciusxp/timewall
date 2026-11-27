package it.timewall.app.ui.widget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class WidgetRefreshJobTest {
    @Test
    public void completesFailedItemAndProcessesNextRefresh() {
        ArrayDeque<Integer> items = new ArrayDeque<>(Arrays.asList(1, 2));
        List<Integer> completed = new ArrayList<>();
        List<Integer> processed = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        WidgetRefreshJob.drain(
                () -> true,
                items::poll,
                item -> {
                    if (item == 1) throw new IllegalArgumentException("Invalid work");
                    processed.add(item);
                },
                completed::add,
                () -> failures.add("failed"));

        assertEquals(Arrays.asList(1, 2), completed);
        assertEquals(Arrays.asList(2), processed);
        assertEquals(Arrays.asList("failed"), failures);
    }

    @Test
    public void stoppingDuringRefreshLeavesWorkForRedelivery() {
        ArrayDeque<Integer> items = new ArrayDeque<>(Arrays.asList(1, 2));
        AtomicBoolean active = new AtomicBoolean(true);
        List<Integer> completed = new ArrayList<>();

        WidgetRefreshJob.drain(
                active::get,
                items::poll,
                item -> active.set(false),
                completed::add,
                () -> {
                    throw new AssertionError("Unexpected failure");
                });

        assertEquals(0, completed.size());
        assertEquals(Integer.valueOf(2), items.peek());
    }
}
