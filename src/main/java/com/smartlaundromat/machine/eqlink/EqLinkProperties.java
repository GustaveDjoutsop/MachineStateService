package com.smartlaundromat.machine.eqlink;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration properties for EQLink cloud integration.
 *
 * <p>EQLink is a SaaS platform that connects washing machines / dryers to the cloud
 * via a hardware IoT module. This service can use EQLink's REST API as an alternative
 * (or complement) to direct MQTT for machine control and state monitoring.
 *
 * <p>Set {@code eqlink.enabled=true} in your config to activate the integration.
 * When disabled (default), all machine commands go through MQTT as before.
 *
 * <p>Example {@code application.yml}:
 * <pre>{@code
 * eqlink:
 *   enabled: true
 *   base-url: https://api.eqlink.top
 *   api-key: YOUR_EQLINK_API_KEY
 *   webhook-secret: YOUR_EQLINK_WEBHOOK_SECRET
 *   poll-interval-ms: 30000
 *   machine-id-mapping:
 *     washer_01: EQ-DEVICE-W01
 *     washer_02: EQ-DEVICE-W02
 * }</pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "eqlink")
public class EqLinkProperties {

    /** Master switch — set to true to activate EQLink cloud integration. */
    private boolean enabled = false;

    /** EQLink REST API base URL (confirm with EQLink support). */
    private String baseUrl = "https://api.eqlink.top";

    /** Bearer API key issued by EQLink for your account. */
    private String apiKey;

    /**
     * HMAC secret used to verify signatures on incoming EQLink webhooks.
     * Configure the same value in the EQLink dashboard under webhook settings.
     */
    private String webhookSecret;

    /**
     * How often (ms) to poll EQLink for machine states.
     * Only used when {@code enabled=true}. Default: 30 000 ms.
     */
    private long pollIntervalMs = 30_000;

    /**
     * Maps internal machine IDs (e.g. {@code washer_01}) to EQLink device IDs
     * (e.g. {@code EQ-DEVICE-W01}). Only machines present in this map can be
     * controlled via EQLink; others fall back to MQTT.
     */
    private Map<String, String> machineIdMapping = new HashMap<>();

    /**
     * Resolves the EQLink device ID for a given internal machine ID.
     *
     * @param machineId internal ID (e.g. {@code washer_01})
     * @return EQLink device ID, or empty if not mapped / blank
     */
    public Optional<String> resolveDeviceId(String machineId) {
        return Optional.ofNullable(machineIdMapping.get(machineId))
                .filter(id -> id != null && !id.isBlank());
    }

    public boolean isFullyConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
