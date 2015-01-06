package com.boundary.metrics.ipmi;

import com.boundary.metrics.ipmi.client.meter.manager.MeterManagerClient;
import com.boundary.metrics.ipmi.client.meter.manager.MeterMetadata;
import com.boundary.metrics.ipmi.client.metrics.MetricsClient;
import com.boundary.metrics.ipmi.poller.IPMIMetricsPoller;
import com.boundary.metrics.ipmi.poller.MonitoredEntity;
import com.boundary.metrics.ipmi.poller.MonitoredMetric;
import com.boundary.metrics.ipmi.resources.MonitoredEntitiesResource;
import com.google.common.collect.Maps;
import com.sun.jersey.api.client.Client;
import com.veraxsystems.vxipmi.api.sync.IpmiConnector;
import io.dropwizard.Application;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class IPMIPoller extends Application<IPMIPollerConfiguration> {

    public static void main(String[] args) throws Exception {
        new IPMIPoller().run(args);
    }

    @Override
    public String getName() {
        return "IPMI-Poller";
    }

    @Override
    public void initialize(Bootstrap<IPMIPollerConfiguration> bootstrap) { }

    @Override
    public void run(IPMIPollerConfiguration configuration, Environment environment) throws Exception {
        /**
         * Create and register clients
         */
        final Client httpClient = new JerseyClientBuilder(environment)
                .using(configuration.getClient())
                .build("http-client");
        final MeterManagerClient meterManagerClient = configuration.getMeterManagerClient().build(httpClient);
        final MetricsClient metricsClient = configuration.getMetricsClient().build(httpClient);
        final ScheduledExecutorService scheduler = environment.lifecycle().scheduledExecutorService("ipmi-poller")
                .threads(configuration.getMonitoredEntities().size() < Runtime.getRuntime().availableProcessors()
                        ? configuration.getMonitoredEntities().size()
                        : Runtime.getRuntime().availableProcessors())
                .build();
        environment.jersey().register(new MonitoredEntitiesResource());
        final IpmiConnector connector = new IpmiConnector(0); // Share the same IpmiConnector

        /**
         * Create metrics
         */
        final String authentication = configuration.getMetricsApiKey();
        Map<String, MonitoredMetric.MetricUnit> metrics = Maps.newHashMap();
        for (MonitoredEntity e : configuration.getMonitoredEntities()) {
            for (MonitoredMetric m : e.getSensors().values()) {
                metrics.put(m.getMetricDisplayName(), m.getUnit());
            }
        }
        for (Map.Entry<String, MonitoredMetric.MetricUnit> m : metrics.entrySet()) {
            metricsClient.createMetric(authentication, m.getKey(), m.getValue().name());
        }

        /**
         * Start pollers for each configured entity
         */
        for (MonitoredEntity e : configuration.getMonitoredEntities()) {
            MeterMetadata meter;
            if (e.getMeterId().isPresent()) {
                meter = meterManagerClient.getMeterMetadataById(configuration.getOrgId(), e.getMeterId().get()).get();
            } else {
                meter = meterManagerClient.createOrGetMeterMetadata(configuration.getOrgId(), e.getAddress().getHostName());
            }
            IPMIMetricsPoller poller = new IPMIMetricsPoller(e, meter.getObservationDomainId(), authentication, metricsClient, connector);
            environment.metrics().registerAll(poller);
            scheduler.scheduleAtFixedRate(poller, 1, configuration.getPollFrequency().toSeconds(), TimeUnit.SECONDS);
        }

        environment.lifecycle().manage(new Managed() {
            @Override
            public void start() throws Exception { }

            @Override
            public void stop() throws Exception {
                connector.tearDown();
            }
        });
    }
}
