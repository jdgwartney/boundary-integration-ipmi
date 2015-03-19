package com.boundary.metrics.ipmi;

import com.boundary.metrics.ipmi.client.metrics.MetricsClient;
import com.boundary.metrics.ipmi.poller.MonitoredEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sun.jersey.api.client.Client;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.util.Duration;
import io.dropwizard.validation.MaxDuration;
import io.dropwizard.validation.MinDuration;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class IPMIPollerConfiguration extends Configuration {

    static class MetricClientConfiguration {
        @NotNull
        @JsonProperty
        private URI baseUri = URI.create("https://metrics-api.boundary.com");

        public URI getBaseUri() { return baseUri; }

        public MetricsClient build(Client httpClient) {
            return new MetricsClient(httpClient, getBaseUri());
        }
    }

    @Valid
    @NotNull
    @JsonProperty
    private JerseyClientConfiguration client = new JerseyClientConfiguration();

    public JerseyClientConfiguration getClient() { return client; }

    @JsonProperty
    @Valid
    @NotNull
    private MetricClientConfiguration metricsClient = new MetricClientConfiguration();

    public MetricClientConfiguration getMetricsClient() {
        return metricsClient;
    }

    @JsonProperty
    @Valid
    @NotNull
    private List<MonitoredEntity> monitoredEntities;

    public List<MonitoredEntity> getMonitoredEntities() {
        return monitoredEntities;
    }

    @JsonProperty
    @MinDuration(value = 1, unit = TimeUnit.SECONDS)
    @MaxDuration(value = 1, unit = TimeUnit.DAYS)
    private Duration pollFrequency = Duration.seconds(5);

    public Duration getPollFrequency() {
        return pollFrequency;
    }

    @JsonProperty
    @NotEmpty
    private String orgId;

    public String getOrgId() {
        return orgId;
    }

    @JsonProperty
    @NotEmpty
    private String metricsApiKey;

    public String getMetricsApiKey() {
        return metricsApiKey;
    }

}
