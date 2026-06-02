package com.smartlaundromat.machine.eqlink.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Payload of a webhook event pushed by EQLink to our service.
 *
 * <p>EQLink sends these events for:
 * <ul>
 *   <li>{@code machine.state_changed} — machine transitioned to a new state</li>
 *   <li>{@code machine.fault}         — an error or fault was detected</li>
 *   <li>{@code cycle.completed}       — a wash/dry cycle finished</li>
 *   <li>{@code machine.offline}       — device lost connectivity</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EqWebhookEvent {

    /**
     * Event type string, e.g. {@code machine.state_changed}, {@code cycle.completed}.
     */
    @JsonProperty("event_type")
    private String eventType;

    /** EQLink device ID that triggered the event. */
    @JsonProperty("device_id")
    private String deviceId;

    /** UTC timestamp when the event occurred. */
    @JsonProperty("timestamp")
    private Instant timestamp;

    /**
     * Event-specific payload. For {@code machine.state_changed}: includes
     * {@code status}, {@code previous_status}. For {@code cycle.completed}:
     * includes {@code duration_seconds}, {@code program}.
     */
    @JsonProperty("payload")
    private Map<String, Object> payload;
}
