package com.boundary.metrics.ipmi.poller;

import javax.annotation.concurrent.Immutable;
import java.net.InetAddress;
import java.util.List;

@Immutable
public class MonitoredEntity {

    public final InetAddress address;
    public final String username;
    public final String password;
    public final List<MonitoredMetric> sensors;

    public MonitoredEntity(InetAddress a, String u, String p, List<MonitoredMetric> ss) {
        address = a;
        username = u;
        password = p;
        sensors = ss;
    }
}
