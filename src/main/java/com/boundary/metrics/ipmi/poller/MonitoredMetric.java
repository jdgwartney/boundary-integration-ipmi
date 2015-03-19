package com.boundary.metrics.ipmi.poller;

import com.boundary.metrics.ipmi.client.metrics.MetricUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class MonitoredMetric {

    public enum MetricUnit { percent, number, bytecount, duration; }
    public enum Aggregate { avg, sum, min, max; }

    @JsonProperty
    @NotEmpty
    private String metricName;

    @JsonProperty
    @Min(1)
    @Max(2^16)
    private int ipmiSensorId;

    @JsonProperty
    @NotNull
    private MetricUnit unit = MetricUnit.number;

    @JsonProperty
    @NotNull
    private Aggregate aggregate = Aggregate.avg;

    @JsonProperty
    private String shortName;
    @JsonProperty
    private String description;

    public String getName() { return MetricUtils.normalizeMetricName(metricName); }
    public String getDescription() { return description != null ? description : metricName; }
    public String getDisplayName() { return metricName; }
    public String getDisplayNameShort() { return shortName != null ? shortName : metricName; }
    public MetricUnit getUnit() { return unit; }
    public Aggregate getDefaultAggregate() { return aggregate; }
    public int getIpmiSensorId() { return ipmiSensorId; }
}
