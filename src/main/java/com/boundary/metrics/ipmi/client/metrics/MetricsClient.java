package com.boundary.metrics.ipmi.client.metrics;

import com.boundary.metrics.ipmi.poller.MonitoredMetric;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.sun.jersey.api.client.AsyncWebResource;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.async.TypeListener;
import com.sun.jersey.core.util.Base64;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.google.common.base.Preconditions.checkNotNull;

public class MetricsClient {

    private final WebResource baseResource;
    private final AsyncWebResource asyncWebResource;

    private static final Joiner PATH_JOINER = Joiner.on('/');
    private static final Logger LOG = LoggerFactory.getLogger(MetricsClient.class);

    public MetricsClient(Client client, URI baseUrl) {
        checkNotNull(client);
        checkNotNull(baseUrl);
        this.baseResource = client.resource(baseUrl);
        this.asyncWebResource = client.asyncResource(baseUrl);
    }

    public void createMetric(String authentication, String metricName, String unit) {
        CreateUpdateMetric metricRequest = new CreateUpdateMetric(metricName, unit);
        ClientResponse response = baseResource.path(PATH_JOINER.join("v1", "metrics", metricRequest.getName()))
                .header(HttpHeaders.AUTHORIZATION, "Basic " + new String(Base64.encode(authentication), Charsets.US_ASCII))
                .entity(metricRequest, MediaType.APPLICATION_JSON_TYPE)
                .put(ClientResponse.class);
        response.close();
    }

    public void addMeasurements(String authentication, String sourceId, Map<String, Number> measurements, Optional<DateTime> optionalTimestamp) {
        List<List<Object>> payload = Lists.newArrayList();
        final long timestamp = optionalTimestamp.or(new DateTime()).getMillis();
        for (Map.Entry<String, Number> m : measurements.entrySet()) {
            payload.add(ImmutableList.<Object>of(sourceId, m.getKey(), m.getValue(), timestamp));
        }
        asyncWebResource.path(PATH_JOINER.join("v1", "measurements"))
                .header(HttpHeaders.AUTHORIZATION, "Basic " + new String(Base64.encode(authentication), Charsets.US_ASCII))
                .entity(payload, MediaType.APPLICATION_JSON_TYPE)
                .post(new TypeListener<ClientResponse>(ClientResponse.class) {
                    @Override
                    public void onComplete(Future<ClientResponse> f) throws InterruptedException {
                        try {
                            ClientResponse response = f.get();
                            response.close();
                            if (Response.Status.OK.getStatusCode() != response.getStatus()) {
                                LOG.error("Unexpected response adding measurements: {}", response.getStatusInfo());
                                throw new WebApplicationException(response.getStatus());
                            }
                            response.close();
                        } catch (ExecutionException e) {
                            LOG.error("Interrupted trying to add measurement");
                        }
                    }
                });
    }
}
