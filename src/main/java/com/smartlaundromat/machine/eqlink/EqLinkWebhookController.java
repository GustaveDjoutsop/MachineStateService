package com.smartlaundromat.machine.eqlink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlaundromat.machine.eqlink.dto.EqWebhookEvent;
import com.smartlaundromat.machine.model.Machine;
import com.smartlaundromat.machine.model.MachineEvent;
import com.smartlaundromat.machine.model.enums.CycleType;
import com.smartlaundromat.machine.model.enums.MachineStatus;
import com.smartlaundromat.machine.repository.MachineEventRepository;
import com.smartlaundromat.machine.repository.MachineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * Receives real-time push events from EQLink's webhook system.
 *
 * <p>This endpoint is <strong>public</strong> (no Bearer token required) — it is
 * secured by HMAC-SHA256 signature verification instead (same pattern as CamPay).
 * Configure your public URL in the EQLink dashboard as:
 * {@code https://your-host/api/eqlink/webhook}.
 *
 * <p>The endpoint is always registered regardless of {@code eqlink.enabled} so that
 * EQLink can still deliver events even during a configuration change; events are simply
 * logged if EQLink is disabled.
 */
@RestController
@RequestMapping("/api/eqlink")
@Slf4j
@RequiredArgsConstructor
public class EqLinkWebhookController {

    private final EqLinkProperties props;
    private final MachineRepository machineRepository;
    private final MachineEventRepository machineEventRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleEvent(
            @RequestHeader(value = "X-EQLink-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        log.debug("EQLink webhook received (size={})", rawBody.length());

        // Verify HMAC signature if a webhook secret is configured
        if (StringUtils.hasText(props.getWebhookSecret())) {
            if (!isValidSignature(rawBody, signature)) {
                log.warn("Invalid EQLink webhook signature — rejecting request");
                return ResponseEntity.status(401).build();
            }
        }

        try {
            EqWebhookEvent event = objectMapper.readValue(rawBody, EqWebhookEvent.class);
            log.info("EQLink event: type={}, device={}", event.getEventType(), event.getDeviceId());
            processEvent(event);
        } catch (Exception e) {
            log.error("Failed to parse EQLink webhook: {}", e.getMessage());
        }

        // Always return 200 quickly; heavy processing should be async
        return ResponseEntity.ok().build();
    }

    // ── Event routing ─────────────────────────────────────────────────────────

    private void processEvent(EqWebhookEvent event) {
        String internalMachineId = resolveInternalId(event.getDeviceId());
        if (internalMachineId == null) {
            log.debug("EQLink device {} has no internal mapping — ignoring event", event.getDeviceId());
            return;
        }

        switch (event.getEventType() != null ? event.getEventType() : "") {
            case "machine.state_changed" -> onStateChanged(internalMachineId, event);
            case "machine.fault"         -> onFault(internalMachineId, event);
            case "cycle.completed"       -> onCycleCompleted(internalMachineId, event);
            case "machine.offline"       -> onOffline(internalMachineId);
            default -> log.debug("EQLink: unhandled event type '{}'", event.getEventType());
        }
    }

    private void onStateChanged(String machineId, EqWebhookEvent event) {
        String newStatusStr = extractPayloadString(event, "status");
        if (newStatusStr == null) {
            return;
        }
        MachineStatus newStatus = mapStatus(newStatusStr);
        updateMachineStatus(machineId, newStatus, "EQLink state_changed: " + newStatusStr);
    }

    private void onFault(String machineId, EqWebhookEvent event) {
        String errorCode = extractPayloadString(event, "error_code");
        machineRepository.findByMachineId(machineId).ifPresent(machine -> {
            machine.setStatus(MachineStatus.ERROR);
            machine.setErrorCode(errorCode);
            machine.setErrorMessage("EQLink fault event: " + errorCode);
            machine.setLastHeartbeat(LocalDateTime.now());
            machineRepository.save(machine);
            recordEvent(machineId, "EQLINK_FAULT", null, MachineStatus.ERROR.name(),
                    "EQLink fault: " + errorCode, null, null);
            log.warn("EQLink fault on machine {}: {}", machineId, errorCode);
        });
    }

    private void onCycleCompleted(String machineId, EqWebhookEvent event) {
        machineRepository.findByMachineId(machineId).ifPresent(machine -> {
            machine.setStatus(MachineStatus.FINISHED);
            machine.setCurrentCycleType(CycleType.NONE);
            machine.setCycleProgress(100);
            machine.setDoorLocked(false);
            machine.setTotalCycles(machine.getTotalCycles() + 1);
            machine.setCyclesSinceService(machine.getCyclesSinceService() + 1);
            machine.setLastHeartbeat(LocalDateTime.now());
            machineRepository.save(machine);
            recordEvent(machineId, "EQLINK_CYCLE_COMPLETED", MachineStatus.RUNNING.name(),
                    MachineStatus.FINISHED.name(), "EQLink cycle.completed event", null, null);
            log.info("EQLink cycle completed on machine {}", machineId);
        });
    }

    private void onOffline(String machineId) {
        updateMachineStatus(machineId, MachineStatus.OFFLINE, "EQLink machine.offline event");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateMachineStatus(String machineId, MachineStatus newStatus, String details) {
        machineRepository.findByMachineId(machineId).ifPresent(machine -> {
            String previousStatus = machine.getStatus().name();
            machine.setStatus(newStatus);
            machine.setIsOnline(newStatus != MachineStatus.OFFLINE);
            machine.setLastHeartbeat(LocalDateTime.now());
            machineRepository.save(machine);
            recordEvent(machineId, "EQLINK_STATUS_CHANGE", previousStatus,
                    newStatus.name(), details, null, null);
        });
    }

    private void recordEvent(String machineId, String eventType, String prevStatus,
                             String newStatus, String details, String cardUid, String txRef) {
        MachineEvent event = MachineEvent.builder()
                .machineId(machineId)
                .eventType(eventType)
                .previousStatus(prevStatus)
                .newStatus(newStatus)
                .details(details)
                .rfidCardUid(cardUid)
                .transactionReference(txRef)
                .build();
        machineEventRepository.save(event);
    }

    private String resolveInternalId(String eqDeviceId) {
        return props.getMachineIdMapping().entrySet().stream()
                .filter(e -> eqDeviceId != null && eqDeviceId.equals(e.getValue()))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private String extractPayloadString(EqWebhookEvent event, String key) {
        if (event.getPayload() == null) {
            return null;
        }
        Object val = event.getPayload().get(key);
        return val instanceof String s ? s : null;
    }

    private MachineStatus mapStatus(String eqStatus) {
        if (eqStatus == null) {
            return MachineStatus.IDLE;
        }
        return switch (eqStatus.toLowerCase()) {
            case "running" -> MachineStatus.RUNNING;
            case "fault"   -> MachineStatus.ERROR;
            case "offline" -> MachineStatus.OFFLINE;
            default        -> MachineStatus.IDLE;
        };
    }

    private boolean isValidSignature(String body, String receivedSig) {
        if (!StringUtils.hasText(receivedSig)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    props.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : computed) {
                hex.append(String.format("%02x", b));
            }
            return MessageDigest.isEqual(
                    hex.toString().getBytes(StandardCharsets.UTF_8),
                    receivedSig.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("EQLink signature verification error: {}", e.getMessage());
            return false;
        }
    }
}
