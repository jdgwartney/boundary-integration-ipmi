package com.boundary.metrics.ipmi.poller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import javax.annotation.concurrent.Immutable;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

@Immutable
public class MonitoredEntity {

    private final InetAddress address;
    private final String username;
    private final String password;
    private final Map<Integer, MonitoredMetric> sensors;
    private final Optional<String> meterId;

    public MonitoredEntity(@JsonProperty("host") InetAddress address,
                           @JsonProperty("meterId") Optional<String> meterId,
                           @JsonProperty("username") String username,
                           @JsonProperty("password") String password,
                           @JsonProperty("sensors") Collection<MonitoredMetric> sensors) {
        this.username = checkNotNull(username);
        this.password = checkNotNull(password);
        this.address = checkNotNull(address);
        ImmutableMap.Builder<Integer, MonitoredMetric> sensorsBuilder = ImmutableMap.builder();
        for (MonitoredMetric m : sensors) {
            sensorsBuilder.put(m.getIpmiSensorId(), m);
        }
        this.sensors = sensorsBuilder.build();
        this.meterId = meterId;
    }

    public InetAddress getAddress() {
        return address;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Map<Integer, MonitoredMetric> getSensors() {
        return sensors;
    }

    public Optional<String> getMeterId() {
        return meterId;
    }
}
