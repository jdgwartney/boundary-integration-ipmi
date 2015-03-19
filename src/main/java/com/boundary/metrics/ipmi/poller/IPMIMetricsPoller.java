package com.boundary.metrics.ipmi.poller;

import com.boundary.metrics.ipmi.client.metrics.MetricsClient;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.veraxsystems.vxipmi.api.async.ConnectionHandle;
import com.veraxsystems.vxipmi.api.sync.IpmiConnector;
import com.veraxsystems.vxipmi.coding.commands.IpmiVersion;
import com.veraxsystems.vxipmi.coding.commands.PrivilegeLevel;
import com.veraxsystems.vxipmi.coding.commands.sdr.*;
import com.veraxsystems.vxipmi.coding.commands.sdr.record.FullSensorRecord;
import com.veraxsystems.vxipmi.coding.commands.sdr.record.RateUnit;
import com.veraxsystems.vxipmi.coding.commands.sdr.record.SensorRecord;
import com.veraxsystems.vxipmi.coding.payload.CompletionCode;
import com.veraxsystems.vxipmi.coding.payload.lan.IPMIException;
import com.veraxsystems.vxipmi.coding.protocol.AuthenticationType;
import com.veraxsystems.vxipmi.coding.security.CipherSuite;
import com.veraxsystems.vxipmi.common.TypeConverter;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IPMIMetricsPoller implements Runnable, MetricSet {

    private static final Logger LOG = LoggerFactory.getLogger(IPMIMetricsPoller.class);

    /**
     * Size of the initial GetSdr message to get record header and size
     */
    private static final int INITIAL_CHUNK_SIZE = 8;

    /**
     * Chunk size depending on buffer size of the IPMI server. Bigger values will improve performance. If server is
     * returning "Cannot return number of requested data bytes." error during GetSdr command, CHUNK_SIZE should be
     * decreased.
     */
    private static final int CHUNK_SIZE = 256;

    /**
     * Size of SDR record header
     */
    private static final int HEADER_SIZE = 5;

    /**
     * This is the value of Last Record ID (FFFFh). In order to retrieve the full set of SDR records, client must repeat
     * reading SDR records until MAX_REPO_RECORD_ID is returned as next record ID. For further information see section
     * 33.12 of the IPMI specification ver. 2.0
     */
    private static final int MAX_REPO_RECORD_ID = 65535;

    private final MetricsClient metricsClient;
    private final MonitoredEntity entity;
    private final String metricAuthentication;
    private final IpmiConnector connector;
    private final ConnectionHandle handle;

    private final AtomicBoolean polling = new AtomicBoolean(false);
    private Map<Integer, FullSensorRecord> sensors;

    private final Timer metricsFetchTimer = new Timer();

    public IPMIMetricsPoller(MonitoredEntity entity, String metricAuthentication, MetricsClient metricClient, IpmiConnector ipmiConnector) throws Exception {
        this.entity = entity;
        this.metricAuthentication = metricAuthentication;
        this.metricsClient = metricClient;
        this.connector = ipmiConnector;

        // start the session to the remote host. We assume, that two-key
        // authentication isn't enabled, so BMC key is left null (see
        // #startSession for details).
        handle = startSession(connector, entity.getAddress(),
                entity.getUsername(), entity.getPassword(), "", PrivilegeLevel.User);
    }

    @Override
    public void run() {
        if (polling.compareAndSet(false, true)) {
            try {
                if (sensors == null) {
                    sensors = getStaticSensorRecords();
                }
                collectMeasurements();
            } catch (Exception e) {
                LOG.error("Failed to collect sensor metrics", e);
            } finally {
                polling.set(false);
            }
        } else {
            LOG.warn("Unable to grab polling lock, previous poll still running");
        }
    }

    private Map<Integer, FullSensorRecord> getStaticSensorRecords() throws Exception {
        Map<Integer, FullSensorRecord> sensors = Maps.newHashMap();

        // Id 0 indicates first record in SDR. Next IDs can be retrieved from
        // records - they are organized in a list and there is no BMC command to
        // get all of them.
        AtomicInteger nextRecId = new AtomicInteger(0);

        // Some BMCs allow getting sensor records without reservation, so we try
        // to do it that way first
        int reservationId = 0;
        int lastReservationId = -1;

        // We get sensor data until we encounter ID = 65535 which means that
        // this record is the last one.
        while (nextRecId.get() < MAX_REPO_RECORD_ID) {
            //for (int nextRecId : records) {
            try {
                // Populate the sensor record and get ID of the next record in
                // repository (see #getSensorData for details).
                SensorRecord record = getSensorData(connector, handle, reservationId, nextRecId);

                // We check if the received record is either FullSensorRecord or
                // CompactSensorRecord, since these types have readings
                // associated with them (see IPMI specification for details).
                if (record instanceof FullSensorRecord) {
                    FullSensorRecord fsr = (FullSensorRecord) record;
                    int recordReadingId = TypeConverter.byteToInt(fsr.getSensorNumber());
                    if (recordReadingId >= 0) {
                        sensors.put(recordReadingId, fsr);
                        LOG.info("{} Found sensor {} (ID: {}, Rate: {})", entity.getAddress().getHostAddress(), fsr.getName(), fsr.getId(), fsr.getSensorBaseUnit().toString());
                    }
                }
            } catch (IPMIException e) {

                System.out.println("Getting new reservation ID");

                System.out.println("156: " + e.getMessage());

                // If getting sensor data failed, we check if it already failed
                // with this reservation ID.
                if (lastReservationId == reservationId)
                    throw e;
                lastReservationId = reservationId;

                // If the cause of the failure was canceling of the
                // reservation, we get new reservationId and retry. This can
                // happen many times during getting all sensors, since BMC can't
                // manage parallel sessions and invalidates old one if new one
                // appears.
                reservationId = ((ReserveSdrRepositoryResponseData) connector
                        .sendMessage(handle, new ReserveSdrRepository(IpmiVersion.V20, handle.getCipherSuite(),
                                AuthenticationType.RMCPPlus))).getReservationId();
            }
        }

        return sensors;
    }

    private void collectMeasurements() throws Exception {
        Timer.Context timerContext = metricsFetchTimer.time();
        Map<String, Number> measurements = Maps.newHashMap();
        for (Map.Entry<Integer, FullSensorRecord> r : sensors.entrySet()) {
            int recordReadingId = r.getKey();
            FullSensorRecord record = r.getValue();
            String value;
            String name = record.getName();

            // If our record has got a reading associated, we get request
            // for it
            try {
                if (recordReadingId >= 0 && entity.getSensors().containsKey(record.getId())) {
                    GetSensorReadingResponseData data2 = (GetSensorReadingResponseData) connector
                            .sendMessage(handle, new GetSensorReading(IpmiVersion.V20, handle.getCipherSuite(),
                                    AuthenticationType.RMCPPlus, recordReadingId));
                    // Parse sensor reading using information retrieved
                    // from sensor record. See
                    // FullSensorRecord#calcFormula for details.
                    measurements.put(entity.getSensors().get(record.getId()).getMetricName(), data2.getSensorReading(record));
                    value = data2.getSensorReading(record) + " " + record.getSensorBaseUnit().toString()
                            + (record.getRateUnit() != RateUnit.None ? " per " + record.getRateUnit() : "");
                    LOG.info("{} ({}/{}) {} = {}", entity.getAddress().getHostAddress(), record.getId(), recordReadingId, name, value);
                }
            } catch (IPMIException e) {
                if (e.getCompletionCode() == CompletionCode.DataNotPresent) {
                    e.printStackTrace();
                } else {
                    throw e;
                }
            }
        }
        timerContext.stop();
        metricsClient.addMeasurements(metricAuthentication, entity.getMeterId(), measurements, Optional.<DateTime>absent());
    }

    private static ConnectionHandle startSession(IpmiConnector connector, InetAddress address, String username,
                                         String password, String bmcKey, PrivilegeLevel privilegeLevel) throws Exception {

        // Create the handle to the connection which will be it's identifier
        ConnectionHandle handle = connector.createConnection(address);

        CipherSuite cs;

        try {
            // Get cipher suites supported by the remote host
            List<CipherSuite> suites = connector.getAvailableCipherSuites(handle);

            if (suites.size() > 3) {
                cs = suites.get(3);
            } else if (suites.size() > 2) {
                cs = suites.get(2);
            } else if (suites.size() > 1) {
                cs = suites.get(1);
            } else {
                cs = suites.get(0);
            }
            // Pick the cipher suite and requested privilege level for the
            // session
            connector.getChannelAuthenticationCapabilities(handle, cs, privilegeLevel);

            // Open the session and authenticate
            connector.openSession(handle, username, password, bmcKey.getBytes());
        } catch (Exception e) {
            connector.closeConnection(handle);
            throw e;
        }

        return handle;
    }

    public SensorRecord getSensorData(IpmiConnector connector, ConnectionHandle handle, int reservationId, AtomicInteger recordId)
            throws Exception {
        try {
            // BMC capabilities are limited - that means that sometimes the
            // record size exceeds maximum size of the message. Since we don't
            // know what is the size of the record, we try to get
            // whole one first
            GetSdrResponseData data = (GetSdrResponseData) connector.sendMessage(handle, new GetSdr(IpmiVersion.V20,
                    handle.getCipherSuite(), AuthenticationType.RMCPPlus, reservationId, recordId.get()));
            // If getting whole record succeeded we create SensorRecord from
            // received data...
            SensorRecord sensorDataToPopulate = null;
            try {
                sensorDataToPopulate = SensorRecord.populateSensorRecord(data.getSensorRecordData());
            } catch (IllegalArgumentException e) {
                // Skip it, one of the sensors isn't understood by this library
            }
            // ... and update the ID of the next record
            recordId.set(data.getNextRecordId());
            return sensorDataToPopulate;
        } catch (IPMIException e) {
            // System.out.println(e.getCompletionCode() + ": " + e.getMessage());
            // The following error codes mean that record is too large to be
            // sent in one chunk. This means we need to split the data in
            // smaller parts.
            if (e.getCompletionCode() == CompletionCode.CannotRespond
                    || e.getCompletionCode() == CompletionCode.UnspecifiedError) {
                // First we get the header of the record to find out its size.
                GetSdrResponseData data = (GetSdrResponseData) connector.sendMessage(handle, new GetSdr(
                        IpmiVersion.V20, handle.getCipherSuite(), AuthenticationType.RMCPPlus, reservationId,
                        recordId.get(), 0, INITIAL_CHUNK_SIZE));
                // The record size is 5th byte of the record. It does not take
                // into account the size of the header, so we need to add it.
                int recSize = TypeConverter.byteToInt(data.getSensorRecordData()[4]) + HEADER_SIZE;
                int read = INITIAL_CHUNK_SIZE;

                byte[] result = new byte[recSize];

                System.arraycopy(data.getSensorRecordData(), 0, result, 0, data.getSensorRecordData().length);

                // We get the rest of the record in chunks (watch out for
                // exceeding the record size, since this will result in BMC's
                // error.
                int chunk_size = CHUNK_SIZE;
                while (read < recSize) {
                    int bytesToRead = chunk_size;
                    if (recSize - read < bytesToRead) {
                        bytesToRead = recSize - read;
                    }
                    try {
                        GetSdrResponseData part = (GetSdrResponseData) connector.sendMessage(handle, new GetSdr(
                                IpmiVersion.V20, handle.getCipherSuite(), AuthenticationType.RMCPPlus, reservationId,
                                recordId.get(), read, bytesToRead));
                        System.arraycopy(part.getSensorRecordData(), 0, result, read, bytesToRead);
                        read += bytesToRead;
                    } catch (IPMIException ee) {
                        if (chunk_size <= 8) throw ee;
                        chunk_size /= 2;
                    }
                }

                // Finally we populate the sensor record with the gathered
                // data...
                SensorRecord sensorDataToPopulate = SensorRecord.populateSensorRecord(result);
                // ... and update the ID of the next record
                recordId.set(data.getNextRecordId());
                return sensorDataToPopulate;
            } else {
                throw e;
            }
        }
    }

    private String toString(FullSensorRecord rec) {
        return Objects.toStringHelper(rec)
                .add("id", rec.getId())
                .add("name", rec.getName())
                .add("accuracy", rec.getAccuracy())
                .add("entityId", rec.getEntityId())
                .add("lowerCriticalThreshold", rec.getLowerCriticalThreshold())
                .add("lowerNonCriticalThreshold", rec.getLowerNonCriticalThreshold())
                .add("lowerNonRecoverableThreshold", rec.getLowerNonRecoverableThreshold())
                .add("nomimalReading", rec.getNominalReading())
                .add("normalMaximum", rec.getNormalMaximum())
                .add("normalMinimum", rec.getNormalMinimum())
                .add("sensorBaseUnit", rec.getSensorBaseUnit())
                .add("sensorMaximumReading", rec.getSensorMaximumReading())
                .add("sensorMinimumReading", rec.getSensorMinmumReading())
                .add("sensorNumber", rec.getSensorNumber())
                .add("sensorOwnerId", rec.getSensorOwnerId())
                .add("sensorResolution", rec.getSensorResolution())
                .add("sensorType", rec.getSensorType())
                .add("tolerance", rec.getTolerance())
                .add("upperCriticalThreshold", rec.getUpperCriticalThreshold())
                .add("upperNonCriticalThreshold", rec.getUpperNonCriticalThreshold())
                .add("upperNonRecoverableThreshold", rec.getUpperNonRecoverableThreshold())
                .toString();
    }

    @Override
    public Map<String, Metric> getMetrics() {
        return ImmutableMap.of(entity.getAddress().getHostAddress() + "-poll-timer", (Metric)metricsFetchTimer);
    }
}
