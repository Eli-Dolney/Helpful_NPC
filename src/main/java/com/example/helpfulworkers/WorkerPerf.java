package com.example.helpfulworkers;

import java.util.Map;
import java.util.TreeMap;

/** Rolling averages of worker job-tick nanoseconds, overall and per role, for /helpfulworkers perf. */
final class WorkerPerf {
    private WorkerPerf() {}

    private static final class Bucket {
        long totalNanos;
        long samples;
        long peakNanos;

        void add(long nanos) {
            totalNanos += nanos;
            samples++;
            if (nanos > peakNanos) peakNanos = nanos;
        }

        String line(String label) {
            double avgUs = (totalNanos / (double) samples) / 1000.0;
            return String.format("%-10s avg %7.1f µs  peak %8.1f µs  samples %d",
                label, avgUs, peakNanos / 1000.0, samples);
        }
    }

    private static final Bucket ALL = new Bucket();
    private static final Map<String, Bucket> BY_ROLE = new TreeMap<>();
    private static long hops;

    static synchronized void record(String role, long nanos) {
        ALL.add(nanos);
        BY_ROLE.computeIfAbsent(role == null || role.isEmpty() ? "idle" : role, r -> new Bucket()).add(nanos);
    }

    /** Count a teleport fallback so navigation changes can be measured. */
    static synchronized void recordHop() { hops++; }

    static synchronized long hops() { return hops; }

    static synchronized String report() {
        if (ALL.samples == 0) return "No worker ticks recorded yet";
        StringBuilder sb = new StringBuilder("Worker perf (job ticks)\n");
        sb.append(ALL.line("all")).append('\n');
        for (Map.Entry<String, Bucket> e : BY_ROLE.entrySet()) sb.append(e.getValue().line(e.getKey())).append('\n');
        sb.append("teleport fallbacks: ").append(hops);
        return sb.toString();
    }

    static synchronized void reset() {
        ALL.totalNanos = 0; ALL.samples = 0; ALL.peakNanos = 0;
        BY_ROLE.clear();
        hops = 0;
    }
}
