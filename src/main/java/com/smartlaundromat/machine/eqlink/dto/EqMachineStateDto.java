package com.smartlaundromat.machine.eqlink.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents the current state of a machine returned by EQLink's
 * {@code GET /v1/devices/{deviceId}/status} endpoint.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EqMachineStateDto {

    /** EQLink device ID. */
    @JsonProperty("device_id")
    private String deviceId;

    /**
     * EQLink machine status: {@code idle}, {@code running}, {@code fault},
     * {@code offline}.
     */
    @JsonProperty("status")
    private String status;

    /** Active program / cycle identifier. Null when idle. */
    @JsonProperty("program")
    private String program;

    /** Estimated remaining time in seconds. Null when idle. */
    @JsonProperty("remaining_seconds")
    private Integer remainingSeconds;

    /** Error or fault code reported by EQLink. Null when healthy. */
    @JsonProperty("error_code")
    private String errorCode;

    /** Whether the machine door is locked. */
    @JsonProperty("door_locked")
    private Boolean doorLocked;
}
