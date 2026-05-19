package com.smartlaundromat.machine.service;

import com.smartlaundromat.machine.config.MachineConfig;
import com.smartlaundromat.machine.dto.*;
import com.smartlaundromat.machine.exception.MachineNotFoundException;
import com.smartlaundromat.machine.exception.MachineNotAvailableException;
import com.smartlaundromat.machine.model.Machine;
import com.smartlaundromat.machine.model.MachineCycle;
import com.smartlaundromat.machine.model.MachineEvent;
import com.smartlaundromat.machine.model.enums.*;
import com.smartlaundromat.machine.mqtt.MqttService;
import com.smartlaundromat.machine.repository.MachineCycleRepository;
import com.smartlaundromat.machine.repository.MachineEventRepository;
import com.smartlaundromat.machine.repository.MachineRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MachineService {

    private final MachineRepository machineRepository;
    private final MachineEventRepository machineEventRepository;
    private final MachineCycleRepository machineCycleRepository;
    private final MachineConfig machineConfig;
    private final MqttService mqttService;

    @PostConstruct
    public void init() {
        mqttService.setMachineService(this);
        initializeMachines();
    }

    private void initializeMachines() {
        for (String machineId : machineConfig.getAvailableIds()) {
            if (!machineRepository.existsByMachineId(machineId)) {
                MachineType type = machineId.startsWith("washer") ? MachineType.WASHER : MachineType.DRYER;
                int position = Integer.parseInt(machineId.replaceAll("\\D+", ""));

                Machine machine = Machine.builder()
                        .machineId(machineId)
                        .type(type)
                        .position(position)
                        .build();
                machineRepository.save(machine);
                log.info("Initialized machine: {}", machineId);
            }
        }
    }

    @Transactional
    public void processTelemetry(TelemetryPayload telemetry) {
        Machine machine = machineRepository.findByMachineId(telemetry.getMachineId())
                .orElse(null);

        if (machine == null) {
            log.warn("Unknown machine telemetry received: {}", telemetry.getMachineId());
            return;
        }

        String previousStatus = machine.getStatus().name();

        if (telemetry.getStatus() != null) {
            try {
                machine.setStatus(MachineStatus.valueOf(telemetry.getStatus().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (telemetry.getCycleType() != null) {
            try {
                machine.setCurrentCycleType(CycleType.valueOf(telemetry.getCycleType().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (telemetry.getCycleProgress() != null) machine.setCycleProgress(telemetry.getCycleProgress());
        if (telemetry.getTemperature() != null) machine.setTemperature(telemetry.getTemperature());
        if (telemetry.getHumidity() != null) machine.setHumidity(telemetry.getHumidity());
        if (telemetry.getWaterLevel() != null) machine.setWaterLevel(telemetry.getWaterLevel());
        if (telemetry.getSpinSpeed() != null) machine.setSpinSpeed(telemetry.getSpinSpeed());
        if (telemetry.getVibration() != null) machine.setVibration(telemetry.getVibration());
        if (telemetry.getDoorLocked() != null) machine.setDoorLocked(telemetry.getDoorLocked());
        if (telemetry.getPowerConsumption() != null) machine.setPowerConsumption(telemetry.getPowerConsumption());
        if (telemetry.getErrorCode() != null) machine.setErrorCode(telemetry.getErrorCode());
        if (telemetry.getErrorMessage() != null) machine.setErrorMessage(telemetry.getErrorMessage());
        if (telemetry.getTotalCycles() != null) machine.setTotalCycles(telemetry.getTotalCycles());

        machine.setIsOnline(true);
        machine.setLastHeartbeat(LocalDateTime.now());

        machineRepository.save(machine);

        String newStatus = machine.getStatus().name();
        if (!previousStatus.equals(newStatus)) {
            recordEvent(machine.getMachineId(), "STATUS_CHANGE",
                    previousStatus, newStatus, "Telemetry update", null, null);
        }
    }

    @Transactional
    public MachineCycle startCycle(StartCycleRequest request) {
        Machine machine = machineRepository.findByMachineId(request.getMachineId())
                .orElseThrow(() -> new MachineNotFoundException("Machine not found: " + request.getMachineId()));

        if (!machine.isAvailable()) {
            throw new MachineNotAvailableException(
                    "Machine " + request.getMachineId() + " is not available (status: " + machine.getStatus() + ")");
        }

        machineCycleRepository.findByMachineIdAndStatus(request.getMachineId(), CycleStatus.IN_PROGRESS)
                .ifPresent(c -> {
                    throw new MachineNotAvailableException("Machine " + request.getMachineId() + " already has an active cycle");
                });

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endsAt = now.plusMinutes(request.getDurationMinutes());

        CycleType cycleType;
        try {
            cycleType = CycleType.valueOf(request.getCycleType().toUpperCase());
        } catch (IllegalArgumentException e) {
            cycleType = CycleType.NORMAL;
        }

        MachineCycle cycle = MachineCycle.builder()
                .machineId(request.getMachineId())
                .cycleType(cycleType)
                .status(CycleStatus.IN_PROGRESS)
                .durationMinutes(request.getDurationMinutes())
                .startedAt(now)
                .endsAt(endsAt)
                .rfidCardUid(request.getRfidCardUid())
                .transactionReference(request.getTransactionReference())
                .pulseCount(request.getPulseCount())
                .build();
        machineCycleRepository.save(cycle);

        machine.setStatus(MachineStatus.RUNNING);
        machine.setCurrentCycleType(cycleType);
        machine.setCycleStartedAt(now);
        machine.setCycleDurationMinutes(request.getDurationMinutes());
        machine.setCycleEndsAt(endsAt);
        machine.setCycleProgress(0);
        machine.setDoorLocked(true);
        machineRepository.save(machine);

        mqttService.sendCommand(request.getMachineId(), "pulse", request.getPulseCount());

        recordEvent(request.getMachineId(), "CYCLE_STARTED",
                "IDLE", "RUNNING",
                "Cycle: " + cycleType + ", Duration: " + request.getDurationMinutes() + "min",
                request.getRfidCardUid(), request.getTransactionReference());

        log.info("Cycle started: machine={}, type={}, duration={}min, endsAt={}",
                request.getMachineId(), cycleType, request.getDurationMinutes(), endsAt);

        return cycle;
    }

    public MachineStatusResponse getMachineStatus(String machineId) {
        Machine machine = machineRepository.findByMachineId(machineId)
                .orElseThrow(() -> new MachineNotFoundException("Machine not found: " + machineId));

        return toStatusResponse(machine);
    }

    public MachineSummaryResponse getAllMachines() {
        List<Machine> machines = machineRepository.findAll();

        List<MachineStatusResponse> responses = machines.stream()
                .map(this::toStatusResponse)
                .collect(Collectors.toList());

        int available = (int) machines.stream().filter(Machine::isAvailable).count();
        int inUse = (int) machines.stream().filter(m -> m.getStatus() == MachineStatus.RUNNING).count();
        int offline = (int) machines.stream().filter(m -> !m.getIsOnline()).count();
        int error = (int) machines.stream().filter(m -> m.getStatus() == MachineStatus.ERROR).count();
        int maintenance = (int) machines.stream().filter(m -> m.getStatus() == MachineStatus.MAINTENANCE).count();

        return MachineSummaryResponse.builder()
                .machines(responses)
                .total(machines.size())
                .available(available)
                .inUse(inUse)
                .offline(offline)
                .error(error)
                .maintenance(maintenance)
                .build();
    }

    public List<MachineEvent> getMachineEvents(String machineId) {
        return machineEventRepository.findTop50ByMachineIdOrderByCreatedAtDesc(machineId);
    }

    public List<MachineCycle> getMachineCycles(String machineId) {
        return machineCycleRepository.findByMachineIdOrderByCreatedAtDesc(machineId);
    }

    @Transactional
    public void sendCommand(String machineId, String action) {
        Machine machine = machineRepository.findByMachineId(machineId)
                .orElseThrow(() -> new MachineNotFoundException("Machine not found: " + machineId));

        mqttService.sendCommand(machineId, action, null);

        recordEvent(machineId, "COMMAND_SENT",
                machine.getStatus().name(), null,
                "Command: " + action, null, null);
    }

    private MachineStatusResponse toStatusResponse(Machine machine) {
        Integer remainingMinutes = null;
        if (machine.getCycleEndsAt() != null && machine.getStatus() == MachineStatus.RUNNING) {
            long remaining = ChronoUnit.MINUTES.between(LocalDateTime.now(), machine.getCycleEndsAt());
            remainingMinutes = (int) Math.max(0, remaining);
        }

        return MachineStatusResponse.builder()
                .machineId(machine.getMachineId())
                .displayName(machine.getDisplayName())
                .type(machine.getType())
                .status(machine.getStatus())
                .online(machine.getIsOnline())
                .available(machine.isAvailable())
                .currentCycleType(machine.getCurrentCycleType())
                .cycleStartedAt(machine.getCycleStartedAt())
                .cycleEndsAt(machine.getCycleEndsAt())
                .cycleProgress(machine.getCycleProgress())
                .remainingMinutes(remainingMinutes)
                .doorLocked(machine.getDoorLocked())
                .temperature(machine.getTemperature())
                .errorCode(machine.getErrorCode())
                .errorMessage(machine.getErrorMessage())
                .lastHeartbeat(machine.getLastHeartbeat())
                .build();
    }

    private void recordEvent(String machineId, String eventType, String previousStatus,
                             String newStatus, String details, String rfidCardUid, String transactionRef) {
        MachineEvent event = MachineEvent.builder()
                .machineId(machineId)
                .eventType(eventType)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .details(details)
                .rfidCardUid(rfidCardUid)
                .transactionReference(transactionRef)
                .build();
        machineEventRepository.save(event);
    }
}
