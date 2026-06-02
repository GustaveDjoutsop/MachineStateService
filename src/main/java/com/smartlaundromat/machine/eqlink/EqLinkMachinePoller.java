package com.smartlaundromat.machine.eqlink;

import com.smartlaundromat.machine.eqlink.dto.EqMachineDto;
import com.smartlaundromat.machine.eqlink.dto.EqMachineStateDto;
import com.smartlaundromat.machine.model.Machine;
import com.smartlaundromat.machine.model.enums.MachineStatus;
import com.smartlaundromat.machine.repository.MachineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled poller that syncs machine states from the EQLink cloud to the local database.
 *
 * <p>This component is only created when {@code eqlink.enabled=true} — when EQLink is
 * disabled the bean is not registered and no polling occurs.
 *
 * <p>The poller acts as a fallback / supplement to real-time webhooks:
 * even if a webhook is missed, the state is reconciled on the next poll cycle.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "eqlink.enabled", havingValue = "true")
public class EqLinkMachinePoller {

    private final EqLinkClient eqLinkClient;
    private final EqLinkProperties props;
    private final MachineRepository machineRepository;

    /**
     * Polls all EQLink devices at the configured interval and reconciles their
     * state with the local database.
     *
     * <p>Uses {@code fixedDelay} (not {@code fixedRate}) so polls don't overlap
     * if EQLink is slow to respond.
     */
    @Scheduled(fixedDelayString = "${eqlink.poll-interval-ms:30000}")
    public void pollAllMachines() {
        if (!props.isFullyConfigured()) {
            return;
        }

        log.debug("EQLink poll starting...");
        List<EqMachineDto> devices = eqLinkClient.getMachines();

        for (EqMachineDto device : devices) {
            // Find the internal machine ID for this EQLink device ID
            props.getMachineIdMapping().entrySet().stream()
                    .filter(e -> device.getDeviceId().equals(e.getValue()))
                    .map(e -> e.getKey())
                    .findFirst()
                    .ifPresent(internalId -> syncState(internalId, device.getDeviceId()));
        }

        log.debug("EQLink poll done — {} devices checked", devices.size());
    }

    // ── private ───────────────────────────────────────────────────────────────

    private void syncState(String internalMachineId, String eqDeviceId) {
        EqMachineStateDto state = eqLinkClient.getMachineState(eqDeviceId);
        if (state == null) {
            return;
        }

        machineRepository.findByMachineId(internalMachineId).ifPresent(machine -> {
            MachineStatus newStatus = mapEqStatus(state.getStatus(), machine.getStatus());
            String previousStatus = machine.getStatus().name();

            machine.setStatus(newStatus);
            machine.setIsOnline(!"offline".equalsIgnoreCase(state.getStatus()));
            machine.setLastHeartbeat(LocalDateTime.now());

            if (state.getErrorCode() != null) {
                machine.setErrorCode(state.getErrorCode());
            }
            if (state.getDoorLocked() != null) {
                machine.setDoorLocked(state.getDoorLocked());
            }

            machineRepository.save(machine);

            if (!previousStatus.equals(newStatus.name())) {
                log.info("EQLink sync: machine={} status changed {} → {}",
                        internalMachineId, previousStatus, newStatus);
            }
        });
    }

    /**
     * Maps EQLink status strings to our internal {@link MachineStatus} enum.
     *
     * @param eqStatus      raw EQLink status string (idle, running, fault, offline)
     * @param currentStatus current internal status (used as fallback)
     */
    private MachineStatus mapEqStatus(String eqStatus, MachineStatus currentStatus) {
        if (eqStatus == null) {
            return currentStatus;
        }
        return switch (eqStatus.toLowerCase()) {
            case "idle"    -> MachineStatus.IDLE;
            case "running" -> MachineStatus.RUNNING;
            case "fault"   -> MachineStatus.ERROR;
            case "offline" -> MachineStatus.OFFLINE;
            default        -> currentStatus;
        };
    }
}
