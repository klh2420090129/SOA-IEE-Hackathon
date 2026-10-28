package org.soa8.freight.common.constants;

public final class EventTopics {
    private EventTopics() {}

    public static final String CARGO_EVENTS_TOPIC = "soa.s06.t08.events";
    public static final String CARGO_EVENTS_DLQ = "soa.s06.t08.events.DLQ";
    public static final String EVENT_TYPE_CARGO_LOAD_REJECTED = "CargoLoadRejected";
    public static final String EVENT_TYPE_CARGO_LOAD_APPROVED = "CargoLoadApproved";
    public static final String SCHEMA_VERSION_1 = "1.0.0";
}
