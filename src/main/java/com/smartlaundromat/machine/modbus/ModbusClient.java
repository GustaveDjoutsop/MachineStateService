package com.smartlaundromat.machine.modbus;

import com.smartlaundromat.machine.modbus.dto.ModbusGatewayRequest;
import com.smartlaundromat.machine.modbus.dto.ModbusGatewayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Modbus RTU client for SX174003A washing machines.
 *
 * <p>RS485 is a serial bus, so this client talks to a <em>serial↔HTTP gateway bridge</em>:
 * it builds the RTU frame ({@link ModbusFrameUtil}), POSTs the hex to the gateway, and parses
 * the slave's reply. In dev the gateway is the WireMock Modbus simulator.
 *
 * <h2>Start sequence</h2>
 * To start a machine the terminal writes three registers in order:
 * <ol>
 *   <li>{@link ModbusRegisters#REG_SELECT_PROGRAM} — select program 1–3</li>
 *   <li>{@link ModbusRegisters#REG_INPUT_COINS}    — inject the required coins</li>
 *   <li>{@link ModbusRegisters#REG_START}          — start</li>
 * </ol>
 *
 * <p>All methods are safe no-ops returning {@code false}/{@code null} when Modbus is disabled
 * or the machine has no slave-address mapping.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModbusClient {

    private final ModbusProperties props;
    private final RestTemplate restTemplate;

    /**
     * Starts a Modbus machine: select program → input coins → start.
     *
     * @param machineId     internal machine ID (must be mapped to a slave address)
     * @param coins         number of coins/pulses to inject (≥ 1)
     * @param programNumber program 1–3 (clamped into range)
     * @return {@code true} when the START write was acknowledged by the slave
     */
    public boolean startMachine(String machineId, int coins, int programNumber) {
        if (!props.isModbusMachine(machineId)) {
            log.debug("Modbus not enabled/mapped for {} — skipping", machineId);
            return false;
        }
        int unitId = props.resolveUnitId(machineId).orElseThrow();
        int program = Math.max(1, Math.min(3, programNumber));
        int coinCount = Math.max(1, coins);

        boolean programOk = write(unitId, ModbusRegisters.REG_SELECT_PROGRAM, program, "select-program");
        boolean coinsOk   = write(unitId, ModbusRegisters.REG_INPUT_COINS, coinCount, "input-coins");
        boolean startOk   = write(unitId, ModbusRegisters.REG_START, 1, "start");

        log.info("Modbus start machine={} unit={} program={} coins={} -> programOk={} coinsOk={} startOk={}",
                machineId, unitId, program, coinCount, programOk, coinsOk, startOk);
        return startOk;
    }

    /** Issues a forced stop ({@link ModbusRegisters#REG_FORCED_STOP}). */
    public boolean forcedStop(String machineId) {
        if (!props.isModbusMachine(machineId)) return false;
        int unitId = props.resolveUnitId(machineId).orElseThrow();
        return write(unitId, ModbusRegisters.REG_FORCED_STOP, 1, "forced-stop");
    }

    /** Resets alarms and silences the buzzer ({@link ModbusRegisters#REG_RESET_ALARM}). */
    public boolean resetAlarm(String machineId) {
        if (!props.isModbusMachine(machineId)) return false;
        int unitId = props.resolveUnitId(machineId).orElseThrow();
        return write(unitId, ModbusRegisters.REG_RESET_ALARM, 1, "reset-alarm");
    }

    /**
     * Reads the 20-register monitor-data block and decodes it.
     *
     * @return decoded monitor data, or {@code null} on error / when not a Modbus machine
     */
    public ModbusMonitorData readMonitorData(String machineId) {
        if (!props.isModbusMachine(machineId)) return null;
        int unitId = props.resolveUnitId(machineId).orElseThrow();
        try {
            byte[] frame = ModbusFrameUtil.buildReadHoldingRegisters(
                    unitId, ModbusRegisters.REG_MONITOR_DATA, ModbusRegisters.MONITOR_DATA_LENGTH);

            ModbusGatewayResponse resp = send(unitId, ModbusRegisters.FUNC_READ_HOLDING, frame);
            if (resp == null || !resp.isSuccess() || resp.getFrameHex() == null) {
                log.warn("Modbus read monitor-data failed for {} (unit {})", machineId, unitId);
                return null;
            }
            byte[] reply = ModbusFrameUtil.fromHex(resp.getFrameHex());
            if (!ModbusFrameUtil.isCrcValid(reply)) {
                log.warn("Modbus read monitor-data CRC mismatch for {} (unit {})", machineId, unitId);
                return null;
            }
            int[] regs = ModbusFrameUtil.parseReadResponse(reply);
            return ModbusMonitorData.from(regs);
        } catch (Exception e) {
            log.error("Modbus readMonitorData {} error: {}", machineId, e.getMessage());
            return null;
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────────

    private boolean write(int unitId, int address, int value, String label) {
        try {
            byte[] frame = ModbusFrameUtil.buildWriteSingleRegister(unitId, address, value);
            ModbusGatewayResponse resp = send(unitId, ModbusRegisters.FUNC_WRITE_MULTIPLE, frame);
            boolean ok = resp != null && resp.isSuccess();
            if (!ok) {
                log.warn("Modbus {} write failed unit={} reg=0x{} value={}",
                        label, unitId, Integer.toHexString(address), value);
            }
            return ok;
        } catch (Exception e) {
            log.error("Modbus {} write error unit={}: {}", label, unitId, e.getMessage());
            return false;
        }
    }

    private ModbusGatewayResponse send(int unitId, int function, byte[] frame) {
        String url = props.getGatewayUrl() + props.getRequestPath();
        ModbusGatewayRequest body = ModbusGatewayRequest.builder()
                .unitId(unitId)
                .function(function)
                .frameHex(ModbusFrameUtil.toHex(frame))
                .build();

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<ModbusGatewayResponse> resp = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, h), ModbusGatewayResponse.class);
        return resp.getBody();
    }
}
