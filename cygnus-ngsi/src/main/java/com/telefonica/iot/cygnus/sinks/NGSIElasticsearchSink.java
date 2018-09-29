/**
 * Copyright 2018 TIS Inc.
 *
 * This file is part of fiware-cygnus (FIWARE project).
 *
 * fiware-cygnus is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version. fiware-cygnus is distributed in the hope that
 * it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with fiware-cygnus. If not, see http://www.gnu.org/licenses/.
 *
 * For those usages not covered by the GNU Affero General Public License please
 * contact with iot_support at tid dot es
 */
package com.telefonica.iot.cygnus.sinks;

import com.telefonica.iot.cygnus.backends.elasticsearch.ElasticsearchBackend;
import com.telefonica.iot.cygnus.backends.elasticsearch.ElasticsearchBackendImpl;
import com.telefonica.iot.cygnus.backends.http.JsonResponse;
import com.telefonica.iot.cygnus.containers.NotifyContextRequest.ContextAttribute;
import com.telefonica.iot.cygnus.containers.NotifyContextRequest.ContextElement;
import com.telefonica.iot.cygnus.errors.CygnusBadConfiguration;
import com.telefonica.iot.cygnus.errors.CygnusCappingError;
import com.telefonica.iot.cygnus.errors.CygnusExpiratingError;
import com.telefonica.iot.cygnus.errors.CygnusPersistenceError;
import com.telefonica.iot.cygnus.errors.CygnusRuntimeError;
import com.telefonica.iot.cygnus.interceptors.NGSIEvent;
import com.telefonica.iot.cygnus.log.CygnusLogger;
import com.telefonica.iot.cygnus.sinks.Enums.DataModel;
import com.telefonica.iot.cygnus.utils.CommonUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.flume.Context;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Sink for Elasticsearch.
 *
 * @author Nobuyuki Matsui
 */
public class NGSIElasticsearchSink extends NGSISink {

    private static final CygnusLogger LOGGER = new CygnusLogger(NGSIElasticsearchSink.class);
    private String elasticsearchHost;
    private String elasticsearchPort;
    private String indexPrefix;
    private String mappingType;
    private boolean ssl;
    private int backendMaxConns;
    private int backendMaxConnsPerRoute;
    private boolean ignoreWhiteSpaces;
    private boolean rowAttrPersistence;
    private String timezone;
    private boolean castValue;
    private int cacheFlashIntervalSec;
    private ElasticsearchBackend persistenceBackend;

    private Map<String, List<Map<String, String>>> aggregations = new ConcurrentHashMap<>();
    private Runnable persistentTask;
    private ScheduledExecutorService scheduler;

    /**
     * Constructor.
     */
    public NGSIElasticsearchSink() {
        super();
    } // NGSIElasticsearchSink

    /**
     * Gets the Elasticsearch host. It is protected due to it is only required for testing purposes.
     * @return The Elasticsearch host
     */
    protected String getElasticsearchHost() {
        return this.elasticsearchHost;
    } // getElasticsearchHost

    /**
     * Gets the Elasticsearch port. It is protected due to it is only required for testing purposes.
     * @return The Elasticsearch port
     */
    protected String getElasticsearchPort() {
        return this.elasticsearchPort;
    } // getElasticsearchPort

    /**
     * Gets if the connections is SSL-enabled. It is protected due to it is only required for testing
     * purposes.
     * @return True if the connection is SSL-enabled, false otherwise
     */
    protected boolean getSSL() {
        return this.ssl;
    } // getSSL

    /**
     * Gets the index prefix. It is protected due to it is only required for testing purposes.
     * @return The index prefix
     */
    protected String getIndexPrefix() {
        return this.indexPrefix;
    } // getIndexPrefix

    /**
     * Gets the mapping type. It is protected due to it is only required for testing purposes.
     * @return The mapping type
     */
    protected String getMappingType() {
        return this.mappingType;
    } // getMappingType

    /**
     * Gets the maximum number of Http connections allowed in the backend. It is protected due to it is only required
     * for testing purposes.
     * @return The maximum number of Http connections allowed in the backend
     */
    protected int getBackendMaxConns() {
        return this.backendMaxConns;
    } // getBackendMaxConns

    /**
     * Gets the maximum number of Http connections per route allowed in the backend. It is protected due to it is only
     * required for testing purposes.
     * @return The maximum number of Http connections per route allowed in the backend
     */
    protected int getBackendMaxConnsPerRoute() {
        return this.backendMaxConnsPerRoute;
    } // getBackendMaxConnsPerRoute

    /**
     * Gets if the empty value is ignored. It is protected due to it is only required for testing purposes.
     * @return True if the empty value is ignored, false otherwise
     */
    protected boolean getIgnoreWhiteSpaces() {
        return this.ignoreWhiteSpaces;
    } // getIgnoreWhiteSpaces

    /**
     * Gets if row-like storing. It is protected due to it is only required for testing purposes.
     * @return True if row-like storing, false column-liken storing
     */

    public boolean getRowAttrPersistence() {
        return this.rowAttrPersistence;
    } // getRowAttrPersistence

    /**
     * Gets the timezone. It is protected due to it is only required for testing purposes.
     * @return The timezone
     */
    protected String getTimezone() {
        return this.timezone;
    } // getTimezone

    /**
     * Gets if to cast the attribute value. It is protected due to it is only required for testing purposes.
     * @return True if to cast attrValue using attrType
     */
    protected boolean getCastValue() {
        return this.castValue;
    } // getCastValue

    /**
     * Gets the time interval (seconds) to flash the memory cache. It is protected due to it is only required for testing purposes.
     * @return The interval seconds
     */
    protected int getCacheFlashIntervalSec() {
        return this.cacheFlashIntervalSec;
    } // getCacheFlashIntervalSec

    /**
     * Returns the persistence backend. It is protected due to it is only required for testing purposes.
     * @return The persistence backend
     */
    protected ElasticsearchBackend getPersistenceBackend() {
        return this.persistenceBackend;
    } // getPersistenceBackend

    /**
     * Sets the persistence backend. It is protected due to it is only required for testing purposes.
     * @param persistenceBackend
     */
    protected void setPersistenceBackend(ElasticsearchBackend persistenceBackend) {
        this.persistenceBackend = persistenceBackend;
    } // setPersistenceBackend

    @Override
    public void configure(Context context) {
        super.configure(context);
        // Techdebt: allow this sink to work with all the data models
        // dataModel = DataModel.DMBYENTITY;
        this.elasticsearchHost = context.getString("elasticsearch_host", "localhost");
        LOGGER.debug("[" + this.getName() + "] Reading configuration (elasticsearch_host=" + this.elasticsearchHost + ")");

        this.elasticsearchPort = context.getString("elasticsearch_port", "9200");
        try {
            int intPort = Integer.parseInt(this.elasticsearchPort);
            if ((intPort >= 0) && (intPort <= 65535)) {
                LOGGER.debug("[" + this.getName() + "] Reading configuration (elasticsearch_port=" + this.elasticsearchPort + ")");
            } else {
                invalidConfiguration = true;
                LOGGER.debug("[" + this.getName() + "] Invalid configuration (elasticsearch_port=" + this.elasticsearchPort
                        + ") -- Must be between 0 and 65535.");
            } // if else
        } catch (Exception e) {
            invalidConfiguration = true;
            LOGGER.debug("[" + this.getName() + "] Invalid configuration (elasticsearch_port=" + this.elasticsearchPort
                  + ") -- Must be a valid number between 0 and 65535.");
        } // try catch

        String sslStr = context.getString("ssl", "false");
        if (sslStr.equals("true") || sslStr.equals("false")) {
            this.ssl = Boolean.valueOf(sslStr);
            LOGGER.debug("[" + this.getName() + "] Reading configuration (ssl=" + sslStr + ")");
        } else  {
            invalidConfiguration = true;
            LOGGER.debug("[" + this.getName() + "] Invalid configuration (ssl="
                + sslStr + ") -- Must be 'true' or 'false'");
        }  // if else

        this.indexPrefix = context.getString("index_prefix", "cygnus");
        LOGGER.debug("[" + this.getName() + "] Reading configuration (index_prefix=" + this.indexPrefix + ")");

        this.mappingType = context.getString("mapping_type", "cygnus_type");
        LOGGER.debug("[" + this.getName() + "] Reading configuration (mapping_type=" + this.mappingType + ")");

        this.backendMaxConns = context.getInteger("backend.max_conns", 500);
        LOGGER.debug("[" + this.getName() + "] Reading configuration (backend.max_conns=" + this.backendMaxConns + ")");

        this.backendMaxConnsPerRoute = context.getInteger("backend.max_conns_per_route", 100);
        LOGGER.debug("[" + this.getName() + "] Reading configuration (backend.max_conns_per_route="
                + this.backendMaxConnsPerRoute + ")");

        String ignoreWhiteSpacesStr = context.getString("ignore_white_spaces", "true");
        if (ignoreWhiteSpacesStr.equals("true") || ignoreWhiteSpacesStr.equals("false")) {
            this.ignoreWhiteSpaces = Boolean.valueOf(ignoreWhiteSpacesStr);
            LOGGER.debug("[" + this.getName() + "] Reading configuration (ignore_white_spaces="
                + ignoreWhiteSpacesStr + ")");
        }  else {
            this.invalidConfiguration = true;
            LOGGER.debug("[" + this.getName() + "] Invalid configuration (ignore_white_spaces="
                + ignoreWhiteSpacesStr + ") -- Must be 'true' or 'false'");
        }  // if else

        String attrPersistenceStr = context.getString("attr_persistence", "row");
        if (attrPersistenceStr.equals("row") || attrPersistenceStr.equals("column")) {
            rowAttrPersistence = attrPersistenceStr.equals("row");
            LOGGER.debug("[" + this.getName() + "] Reading configuration (attr_persistence="
                + attrPersistenceStr + ")");
        } else {
            this.invalidConfiguration = true;
            LOGGER.debug("[" + this.getName() + "] Invalid configuration (attr_persistence="
                + attrPersistenceStr + ") must be 'row' or 'column'");
        }  // if else

        this.timezone = context.getString("timezone", "UTC");
        LOGGER.debug("[" + this.getName() + "] Reading configuration (index_prefix=" + this.indexPrefix + ")");

        String castValueStr = context.getString("cast_value", "false");
        if (castValueStr.equals("true") || castValueStr.equals("false")) {
            this.castValue = Boolean.valueOf(castValueStr);
            LOGGER.debug("[" + this.getName() + "] Reading configuration (cast_value="
                + castValueStr + ")");
        }  else {
            this.castValue = false;
            LOGGER.debug("[" + this.getName() + "] Invalid configuration (cast_value="
                + castValueStr + ") -- Must be 'true' or 'false'");
        }  // if else

        this.cacheFlashIntervalSec = context.getInteger("cache_flash_interval_sec", 0);
        LOGGER.debug("[" + this.getName() + "] Reading configuration (cache_flash_interval_sec="
                + this.cacheFlashIntervalSec + ")");
    } // configure

    @Override
    public void start() {
        try {
            this.persistenceBackend = new ElasticsearchBackendImpl(this.elasticsearchHost, this.elasticsearchPort,
                this.ssl, this.backendMaxConns, this.backendMaxConnsPerRoute);
            String endpoint = this.ssl ? "https://" : "http://" + this.elasticsearchHost + ":" + this.elasticsearchPort;
            LOGGER.debug("[" + this.getName() + "] Elasticsearch persistence backend created (endpoint=" + endpoint + ")");
        } catch (Exception e) {
            LOGGER.error("Error while creating the Elasticsearch persistence backend. Details=" + e.getMessage());
        }
        this.persistentTask = new Runnable() {
            public void run() {
                try {
                    synchronized(NGSIElasticsearchSink.this.aggregations) {
                        for (Map.Entry<String, List<Map<String, String>>> aggregation : NGSIElasticsearchSink.this.aggregations.entrySet()) {
                            String idx = aggregation.getKey();
                            List<Map<String, String>> data = aggregation.getValue();
                            JsonResponse response = NGSIElasticsearchSink.this.persistenceBackend.bulkInsert(idx,
                                    NGSIElasticsearchSink.this.mappingType, data);
                            LOGGER.info("[" + NGSIElasticsearchSink.this.getName() + "] Persisting data at NGSIElasticsearchSink. (index="
                                    + idx + ", type=" + NGSIElasticsearchSink.this.mappingType + ", data=" + data + ")");
                        } // for
                        NGSIElasticsearchSink.this.aggregations.clear();
                    } // synchronized
                } catch (Exception e) {
                    LOGGER.error("Error while persisting data using backend. Details=" + e.getMessage());
                    throw new RuntimeException(e);
                } // try
            }
        };
        // start ScheduledExecutorService to persist data at the cacheFlashIntervalSec intervals
        // if cacheFlashIntervalSec is 0 (default value), the scheduler service does not start and the data is persisted at every time when 'persistBatch' is called
        if (this.cacheFlashIntervalSec != 0) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor();
            this.scheduler.scheduleWithFixedDelay(this.persistentTask, 0, this.cacheFlashIntervalSec, TimeUnit.SECONDS);
            LOGGER.debug("[" + this.getName() + "] ScheduledExecutorService started");
        } // if
        LOGGER.info("[" + this.getName() + "] started NGSIElasticsearchSink gracefully");
        super.start();
    } // start

    @Override
    public void stop() {
        try {
            // stop scheduler if started
            if (this.scheduler != null) {
                this.scheduler.shutdown();
                this.scheduler.awaitTermination(this.cacheFlashIntervalSec * 2, TimeUnit.SECONDS);
                LOGGER.debug("[" + this.getName() + "] ScheduledThreadPoolExecutor stopped");
            }
        } catch (Exception e) {
            LOGGER.error("Error while shutting down the scheduler. Details=" + e.getMessage());
        } finally {
            // persist the remaining data if exists
            this.persistentTask.run();
            LOGGER.debug("[" + this.getName() + "] persisted all data");
        }
        LOGGER.info("[" + this.getName() + "] stopped NGSIElasticsearchSink gracefully");
        super.stop();
    } // stop

    @Override
    void persistBatch(NGSIBatch batch) throws CygnusBadConfiguration, CygnusPersistenceError, CygnusRuntimeError {
        if (batch == null) {
            LOGGER.debug("[" + this.getName() + "] Null batch, nothing to do");
            return;
        } // if

        // Iterate on the destinations
        batch.startIterator();

        while (batch.hasNext()) {
            String destination = batch.getNextDestination();
            LOGGER.debug("[" + this.getName() + "] Processing sub-batch regarding the "
                    + destination + " destination");

            // Get the sub-batch for this destination
            ArrayList<NGSIEvent> events = batch.getNextEvents();

            // Get an aggregator for this destination and initialize it
            ElasticsearchAggregator aggregator = new ElasticsearchAggregator();
            aggregator.initialize(events.get(0));

            for (NGSIEvent event : events) {
                aggregator.aggregate(event);
            } // for

            // Persist the aggregation if cacheFlashIntervalSec == 0
            if (this.cacheFlashIntervalSec == 0) {
                this.persistentTask.run();
            } // if
            batch.setNextPersisted(true);
        } // for
    } // persistBatch

    @Override
    public void capRecords(NGSIBatch batch, long maxRecords) throws CygnusCappingError {
    } // capRecords

    @Override
    public void expirateRecords(long expirationTime) throws CygnusExpiratingError {
    } // expirateRecords

    /**
     * Class for aggregating aggregation.
     */
    private class ElasticsearchAggregator {
        private DateTimeFormatter indexDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        private String index;
        private JSONParser parser;

        public ElasticsearchAggregator() {
            this.parser = new JSONParser();
        } // ElasticsearchAggregator

        public void initialize(NGSIEvent event) throws CygnusBadConfiguration {
            String service = event.getServiceForNaming(enableNameMappings);
            String servicePath = event.getServicePathForNaming(enableGrouping, enableNameMappings);
            this.index = (NGSIElasticsearchSink.this.indexPrefix + "-" + service + servicePath).toLowerCase().replace("/", "-");
            LOGGER.debug("ElasticsearchAggregator initialize (index=" + this.index + ")");
        } // initialize

        public void aggregate(NGSIEvent event) {
            long notifiedRecvTimeTs = event.getRecvTimeTs();

            ContextElement contextElement = event.getContextElement();
            String entityId = contextElement.getId();
            String entityType = contextElement.getType();
            LOGGER.debug("[" + getName() + "] Processing context element (id=" + entityId + ", type="
                    + entityType + ")");

            // iterate on all this context element attributes, if there are attributes
            ArrayList<ContextAttribute> contextAttributes = contextElement.getAttributes();

            if (contextAttributes == null || contextAttributes.isEmpty()) {
                LOGGER.warn("No attributes within the notified entity, nothing is done (id=" + entityId
                        + ", type=" + entityType + ")");
                return;
            } // if
            if (NGSIElasticsearchSink.this.rowAttrPersistence) {
                this.aggregateAsRow(notifiedRecvTimeTs, entityId, entityType, contextAttributes);
            } else {
                this.aggregateAsColumn(notifiedRecvTimeTs, entityId, entityType, contextAttributes);
            }
        } // aggregate

        private void aggregateAsRow(long notifiedRecvTimeTs, String entityId, String entityType, List<ContextAttribute> contextAttributes) {
            for (ContextAttribute contextAttribute : contextAttributes) {
                String attrName = contextAttribute.getName();
                String attrType = contextAttribute.getType();
                String attrValue = contextAttribute.getContextValue(false);
                String attrMetadata = contextAttribute.getContextMetadata();

                // check if the attribute value is based on white spaces
                if (NGSIElasticsearchSink.this.ignoreWhiteSpaces && attrValue.trim().length() == 0) {
                    continue;
                } // if

                TimeRelatedValues v = this.getTimeRelatedValues(notifiedRecvTimeTs, attrMetadata);

                JSONObject jobj = new JSONObject();
                jobj.put("recvTime", v.recvTimeDt.format(DateTimeFormatter.ISO_INSTANT));
                jobj.put("entityId", entityId);
                jobj.put("entityType", entityType);
                jobj.put("attrName", attrName);
                jobj.put("attrType", attrType);

                if (NGSIElasticsearchSink.this.castValue) {
                    jobj.put("attrValue", this.convertValueType(attrType, attrValue));
                } else {
                    jobj.put("attrValue", attrValue);
                }

                if (attrMetadata != null) {
                    try {
                        jobj.put("attrMetadata", parser.parse(attrMetadata));
                    } catch (ParseException e) {
                        jobj.put("attrMetadata", null);
                    }
                } else {
                    jobj.put("attrMetadata", null);
                }

                Map<String, String> elem = new HashMap<>();
                elem.put("recvTimeTs", String.valueOf(v.recvTimeTs));
                elem.put("data", jobj.toJSONString());
                synchronized(NGSIElasticsearchSink.this.aggregations) {
                    NGSIElasticsearchSink.this.aggregations.putIfAbsent(v.idx, new ArrayList<Map<String, String>>());
                    NGSIElasticsearchSink.this.aggregations.get(v.idx).add(elem);
                } // synchronized
            } // for
        } // aggregateAsRow

        private void aggregateAsColumn(long notifiedRecvTimeTs, String entityId, String entityType, List<ContextAttribute> contextAttributes) {
            String firstAttrMetadata = contextAttributes.get(0).getContextMetadata();
            TimeRelatedValues v = this.getTimeRelatedValues(notifiedRecvTimeTs, firstAttrMetadata);

            JSONObject jobj = new JSONObject();
            jobj.put("recvTime", v.recvTimeDt.format(DateTimeFormatter.ISO_INSTANT));
            jobj.put("entityId", entityId);
            jobj.put("entityType", entityType);

            for (ContextAttribute contextAttribute : contextAttributes) {
                String attrName = contextAttribute.getName();
                String attrType = contextAttribute.getType();
                String attrValue = contextAttribute.getContextValue(false);

                // check if the attribute value is based on white spaces
                if (NGSIElasticsearchSink.this.ignoreWhiteSpaces && attrValue.trim().length() == 0) {
                    continue;
                } // if

                if (NGSIElasticsearchSink.this.castValue) {
                    jobj.put(attrName, this.convertValueType(attrType, attrValue));
                } else {
                    jobj.put(attrName, attrValue);
                }
            }

            Map<String, String> elem = new HashMap<>();
            elem.put("recvTimeTs", String.valueOf(v.recvTimeTs));
            elem.put("data", jobj.toJSONString());
            synchronized(NGSIElasticsearchSink.this.aggregations) {
                NGSIElasticsearchSink.this.aggregations.putIfAbsent(v.idx, new ArrayList<Map<String, String>>());
                NGSIElasticsearchSink.this.aggregations.get(v.idx).add(elem);
            } // synchronized
        } // aggregateAsColumn

        private class TimeRelatedValues {
            public Long recvTimeTs;
            public ZonedDateTime recvTimeDt;
            public String idx;
        } // TimeRelatedValues

        private TimeRelatedValues getTimeRelatedValues(long notifiedRecvTimeTs, String attrMetadata) {
            TimeRelatedValues v = new TimeRelatedValues();

            // check if the metadata contains a TimeInstant value; use the notified reception time instead
            Long timeInstant = CommonUtils.getTimeInstant(attrMetadata);
            if (timeInstant != null) {
                v.recvTimeTs = timeInstant;
            } else {
                v.recvTimeTs = notifiedRecvTimeTs;
            } // if else

            v.recvTimeDt = ZonedDateTime.now(Clock.fixed(Instant.ofEpochMilli(v.recvTimeTs),
                  ZoneId.of(NGSIElasticsearchSink.this.timezone)));
            v.idx = this.index + "-" + v.recvTimeDt.format(this.indexDateFormatter);
            return v;
        } // getTimeRelatedValues

        private Object convertValueType(String attrType, String attrValue) {
            String t = attrType.toLowerCase();
            try {
                if (t.startsWith("int")) {
                    return Integer.valueOf(attrValue);
                } else if (t.startsWith("float")) {
                    return Float.valueOf(attrValue);
                } else if (t.startsWith("number") || t.startsWith("double")) {
                    return Double.valueOf(attrValue);
                } else if (t.startsWith("bool")) {
                    return Boolean.valueOf(attrValue);
                } else {
                    return attrValue;
                } // if else
            } catch (Exception e) {
                return null;
            } // try
        } // convertValueType
    } // ElasticsearchAggregator
} // NGSIElasticsearchSink
