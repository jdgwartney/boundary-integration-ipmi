package com.boundary.metrics.ipmi.poller;

import com.boundary.metrics.ipmi.client.metrics.MetricUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class MonitoredMetric {

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

    public String getMetricDisplayName() {
        return metricName;
    }

    public String getMetricName() {
        return MetricUtils.normalizeMetricName(metricName);
    }

    public int getIpmiSensorId() {
        return ipmiSensorId;
    }

    public MetricUnit getUnit() {
        return unit;
    }

    public enum MetricUnit {
        percent, number, bytecount, duration;
    }
}
