package com.smartlaundromat.machine.eqlink.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents a single machine/device entry returned by EQLink's
 * {@code GET /v1/devices} endpoint.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EqMachineDto {

    /** EQLink's unique device identifier. */
    @JsonProperty("device_id")
    private String deviceId;

    /** Human-readable device name configured in the EQLink dashboard. */
    @JsonProperty("device_name")
    private String deviceName;

    /** Device type reported by EQLink (e.g. "WASHER", "DRYER"). */
    @JsonProperty("device_type")
    private String deviceType;

    /** Whether the device is currently online. */
    @JsonProperty("online")
    private Boolean online;

    /** Raw status string from EQLink (e.g. "idle", "running", "fault"). */
    @JsonProperty("status")
    private String status;
}
