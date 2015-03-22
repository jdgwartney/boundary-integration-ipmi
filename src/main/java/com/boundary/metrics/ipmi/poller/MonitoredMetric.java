package com.boundary.metrics.ipmi.poller;

import com.boundary.metrics.ipmi.IPMIPollerConfiguration;

public class MonitoredMetric {

    public static class Metric {
        public final String name;
        public final String description;
        public final String displayName;
        public final String displayNameShort;
        public final String unit;
        public final String aggregate;
        public Metric(IPMIPollerConfiguration.SensorConfiguration s) {
            name = s.metric;
            description = s.description != null ? s.description : "";
            displayName = s.displayName != null ? s.displayName : "";
            displayNameShort = s.displayNameShort != null ? s.displayNameShort : "";
            unit = s.unit.name();
            aggregate = s.defaultAggregate.name();
        }
    }

    public final int ipmiid;
    public final Metric metric;
    public final String source;
    public MonitoredMetric(IPMIPollerConfiguration.SensorConfiguration s, Metric m, String source) {
        ipmiid = s.sensorId;
        this.source = s.source != null ? s.source : source;
        metric = m;
    }
}
