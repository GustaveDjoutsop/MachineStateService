package com.smartlaundromat.machine.eqlink;

import com.smartlaundromat.machine.eqlink.dto.EqMachineDto;
import com.smartlaundromat.machine.eqlink.dto.EqMachineStateDto;
import com.smartlaundromat.machine.eqlink.dto.EqStartCommandRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HTTP client that wraps all calls to the EQLink REST API.
 *
 * <p>All methods return early (no-op / empty) when {@code eqlink.enabled=false}
 * or when the API key is not configured — making the client safe to inject
 * even when EQLink is disabled.
 *
 * <p>Paths are based on EQLink's Open API conventions. Confirm the exact
 * base URL and endpoint paths from EQLink documentation before going live.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EqLinkClient {

    private final EqLinkProperties props;
    private final RestTemplate restTemplate;

    // ── Machine listing ───────────────────────────────────────────────────────

    /**
     * Fetches all devices linked to the EQLink account.
     *
     * @return list of EQLink device descriptors, or empty list if EQLink is disabled
     */
    public List<EqMachineDto> getMachines() {
        if (!props.isFullyConfigured()) {
            return Collections.emptyList();
        }
        try {
            String url = props.getBaseUrl() + "/v1/devices";
            ResponseEntity<EqMachineDto[]> resp = restTemplate.exchange(
                    url, HttpMethod.GET, authHeader(), EqMachineDto[].class);
            EqMachineDto[] body = resp.getBody();
            return body != null ? Arrays.asList(body) : Collections.emptyList();
        } catch (Exception e) {
            log.error("EQLink getMachines failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Machine state ─────────────────────────────────────────────────────────

    /**
     * Retrieves the current state of a single EQLink device.
     *
     * @param eqDeviceId EQLink's own device identifier
     * @return state DTO, or {@code null} if EQLink is disabled or call failed
     */
    public EqMachineStateDto getMachineState(String eqDeviceId) {
        if (!props.isFullyConfigured()) {
            return null;
        }
        try {
            String url = props.getBaseUrl() + "/v1/devices/" + eqDeviceId + "/status";
            ResponseEntity<EqMachineStateDto> resp = restTemplate.exchange(
                    url, HttpMethod.GET, authHeader(), EqMachineStateDto.class);
            return resp.getBody();
        } catch (Exception e) {
            log.error("EQLink getMachineState failed for {}: {}", eqDeviceId, e.getMessage());
            return null;
        }
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    /**
     * Sends a start command to an EQLink device, launching a wash/dry cycle.
     *
     * @param eqDeviceId EQLink device ID
     * @param cmd        program + duration + optional transaction reference
     * @return {@code true} if the command was accepted (2xx), {@code false} otherwise
     */
    public boolean startMachine(String eqDeviceId, EqStartCommandRequest cmd) {
        if (!props.isFullyConfigured()) {
            log.debug("EQLink disabled — skipping start command for device {}", eqDeviceId);
            return false;
        }
        try {
            String url = props.getBaseUrl() + "/v1/devices/" + eqDeviceId + "/start";
            HttpEntity<EqStartCommandRequest> entity = new HttpEntity<>(cmd, buildHeaders());
            ResponseEntity<Void> resp = restTemplate.postForEntity(url, entity, Void.class);
            boolean ok = resp.getStatusCode().is2xxSuccessful();
            if (ok) {
                log.info("EQLink START command accepted for device {} program {}",
                        eqDeviceId, cmd.getProgram());
            } else {
                log.warn("EQLink START command rejected for device {}: HTTP {}",
                        eqDeviceId, resp.getStatusCode());
            }
            return ok;
        } catch (Exception e) {
            log.error("EQLink startMachine failed for {}: {}", eqDeviceId, e.getMessage());
            return false;
        }
    }

    /**
     * Sends a stop command to an EQLink device.
     *
     * @param eqDeviceId EQLink device ID
     */
    public void stopMachine(String eqDeviceId) {
        if (!props.isFullyConfigured()) {
            return;
        }
        try {
            String url = props.getBaseUrl() + "/v1/devices/" + eqDeviceId + "/stop";
            restTemplate.exchange(url, HttpMethod.POST, authHeader(), Void.class);
            log.info("EQLink STOP command sent to device {}", eqDeviceId);
        } catch (Exception e) {
            log.error("EQLink stopMachine failed for {}: {}", eqDeviceId, e.getMessage());
        }
    }

    /**
     * Registers a webhook URL with EQLink so it pushes real-time events.
     * Call this once at startup or when the backend URL changes.
     *
     * @param callbackUrl the publicly reachable URL EQLink will POST events to
     */
    public void registerWebhook(String callbackUrl) {
        if (!props.isFullyConfigured()) {
            return;
        }
        try {
            String url = props.getBaseUrl() + "/v1/webhooks";
            var body = Map.of(
                    "url", callbackUrl,
                    "events", List.of("machine.state_changed", "machine.fault",
                                      "cycle.completed", "machine.offline")
            );
            HttpEntity<?> entity = new HttpEntity<>(body, buildHeaders());
            restTemplate.postForEntity(url, entity, Void.class);
            log.info("EQLink webhook registered: {}", callbackUrl);
        } catch (Exception e) {
            log.warn("EQLink webhook registration failed: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpEntity<Void> authHeader() {
        return new HttpEntity<>(buildHeaders());
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + props.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
