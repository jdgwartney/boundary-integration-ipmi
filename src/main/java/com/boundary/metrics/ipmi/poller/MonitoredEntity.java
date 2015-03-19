package com.boundary.metrics.ipmi.poller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;

import javax.annotation.concurrent.Immutable;
import java.net.InetAddress;
import java.util.Collection;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

@Immutable
public class MonitoredEntity {

    private final InetAddress address;
    private final String username;
    private final String password;
    private final List<MonitoredMetric> sensors;
    private final String meterId;

    public MonitoredEntity(@JsonProperty("host") InetAddress address,
                           @JsonProperty("meterId") String meterId,
                           @JsonProperty("username") String username,
                           @JsonProperty("password") String password,
                           @JsonProperty("sensors") List<MonitoredMetric> sensors) {
        this.username = checkNotNull(username);
        this.password = checkNotNull(password);
        this.address = checkNotNull(address);
        this.sensors = sensors;
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

    public List<MonitoredMetric> getSensors() {
        return sensors;
    }

    public String getMeterId() {
        return meterId;
    }
}
