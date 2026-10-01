package com.example.helpfulworkers;

/** Rolling average of worker job-tick nanoseconds for /helpfulworkers perf. */
final class WorkerPerf {
    private WorkerPerf() {}

    private static long totalNanos;
    private static long samples;
    private static long peakNanos;

    static synchronized void record(long nanos) {
        totalNanos += nanos;
        samples++;
        if (nanos > peakNanos) peakNanos = nanos;
    }

    static synchronized String report() {
        if (samples == 0) return "No worker ticks recorded yet";
        double avgUs = (totalNanos / (double) samples) / 1000.0;
        double peakUs = peakNanos / 1000.0;
        return String.format("Worker perf: avg %.1f µs/tick over %d samples (peak %.1f µs)", avgUs, samples, peakUs);
    }

    static synchronized void reset() {
        totalNanos = 0;
        samples = 0;
        peakNanos = 0;
    }
}
