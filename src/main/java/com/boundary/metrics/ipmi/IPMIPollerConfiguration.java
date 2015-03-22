package com.boundary.metrics.ipmi;

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
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class IPMIPollerConfiguration extends Configuration {

    public static class MetricClientConfiguration {
        @NotNull
        @JsonProperty
        public URI baseUri;
        @NotNull
        @JsonProperty
        public String apiUser;
        @NotNull
        @JsonProperty
        public String apiToken;
    }

    public static class SensorConfiguration {
        public enum Unit { percent, number, bytecount, duration; }
        public enum Aggregate { avg, sum, min, max; }

        @JsonProperty
        @NotNull
        public int sensorId;
        @JsonProperty
        @NotNull
        public String metric;
        @JsonProperty
        public String description;
        @JsonProperty
        public String displayName;
        @JsonProperty
        public String displayNameShort;
        @JsonProperty
        public Unit unit = Unit.number;
        @JsonProperty
        public Aggregate defaultAggregate = Aggregate.avg;
        @JsonProperty
        public String source;
    }

    public static class EntityConfiguration {
        @NotNull
        @JsonProperty
        public InetAddress host;
        @JsonProperty
        public String username;
        @JsonProperty
        public String password;
        @JsonProperty
        public String source;
        @NotNull
        @JsonProperty
        public List<SensorConfiguration> sensors;
    }

    @Valid
    @NotNull
    @JsonProperty
    public JerseyClientConfiguration client = new JerseyClientConfiguration();

    @JsonProperty
    @Valid
    @NotNull
    public MetricClientConfiguration metricsClient = new MetricClientConfiguration();

    @JsonProperty
    @Valid
    @NotEmpty
    public List<EntityConfiguration> monitoredEntities;

    @JsonProperty
    @MinDuration(value = 1, unit = TimeUnit.SECONDS)
    @MaxDuration(value = 1, unit = TimeUnit.DAYS)
    public Duration pollFrequency = Duration.seconds(5);
}
