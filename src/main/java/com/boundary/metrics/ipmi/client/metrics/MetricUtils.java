package com.boundary.metrics.ipmi.client.metrics;

import com.boundary.metrics.ipmi.poller.MonitoredMetric;
import com.google.common.base.Function;

import javax.annotation.Nullable;

public class MetricUtils {

    private MetricUtils() { /* static class */ }

    public static String normalizeMetricName(String name) {
        return name.replace(' ', '_').replace('.', '_').toUpperCase();
        // TODO add more normalization based on only 0-9, A-Z, and _ which cannot lead
    }

    public static Function<MonitoredMetric, Integer> extractSensorId = new Function<MonitoredMetric, Integer>() {
        @Nullable
        @Override
        public Integer apply(@Nullable MonitoredMetric input) {
            return input == null ? null : input.getIpmiSensorId();
        }
    };

}
