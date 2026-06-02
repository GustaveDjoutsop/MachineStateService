package com.smartlaundromat.machine.eqlink.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body sent to EQLink's {@code POST /v1/devices/{deviceId}/start}
 * endpoint to remotely start a wash or dry cycle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EqStartCommandRequest {

    /**
     * Wash/dry program to run (e.g. {@code QUICK_WASH}, {@code INTENSIVE},
     * {@code COTTON_60}). Must match a program code accepted by the EQLink module.
     */
    @JsonProperty("program")
    private String program;

    /** Duration of the cycle in minutes. Optional — EQLink uses a program default if absent. */
    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    /** Internal transaction reference — sent as metadata so EQLink can include it in callbacks. */
    @JsonProperty("transaction_ref")
    private String transactionRef;
}
