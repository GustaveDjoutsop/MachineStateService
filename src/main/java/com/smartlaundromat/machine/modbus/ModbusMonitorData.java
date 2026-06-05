package com.smartlaundromat.machine.modbus;

import lombok.Builder;
import lombok.Data;

/**
 * Decoded view of the 20-register monitor-data block ({@link ModbusRegisters#REG_MONITOR_DATA}).
 * See the "Monitor Data" sheet of the SX174003A protocol.
 */
@Data
@Builder
public class ModbusMonitorData {

    private int machineStatus;   // 0:PowerOn 1:Idle 3:Autorun
    private int doorStatus;      // 0:Idle 1:Open 2:Closed 3:Locked 4:Error ...
    private int errorStatus;     // bit0:Alarm bit1:Warning
    private int remainingHour;
    private int remainingMin;
    private int remainingSec;
    private int waterLevel;      // cm
    private int temperature;     // ℃
    private int speed;           // rpm
    private int programNumber;
    private int operationStep;
    private int coinsRequired;
    private int coinsCurrent;
    private int totalCoins;

    /** Maps the raw register block returned by the slave into a typed view. */
    public static ModbusMonitorData from(int[] r) {
        if (r == null || r.length < ModbusRegisters.MONITOR_DATA_LENGTH) {
            return ModbusMonitorData.builder().build();
        }
        return ModbusMonitorData.builder()
                .machineStatus(r[ModbusRegisters.IDX_MACHINE_STATUS])
                .doorStatus(r[ModbusRegisters.IDX_DOOR_STATUS])
                .errorStatus(r[ModbusRegisters.IDX_ERROR_STATUS])
                .remainingHour(r[ModbusRegisters.IDX_REMAIN_HOUR])
                .remainingMin(r[ModbusRegisters.IDX_REMAIN_MIN])
                .remainingSec(r[ModbusRegisters.IDX_REMAIN_SEC])
                .waterLevel(r[ModbusRegisters.IDX_WATER_LEVEL])
                .temperature(r[ModbusRegisters.IDX_TEMPERATURE])
                .speed(r[ModbusRegisters.IDX_SPEED])
                .programNumber(r[ModbusRegisters.IDX_PROGRAM_NUMBER])
                .operationStep(r[ModbusRegisters.IDX_OPERATION_STEP])
                .coinsRequired(r[ModbusRegisters.IDX_COINS_REQUIRED])
                .coinsCurrent(r[ModbusRegisters.IDX_COINS_CURRENT])
                .totalCoins(r[ModbusRegisters.IDX_TOTAL_COINS])
                .build();
    }

    public boolean isRunning()    { return machineStatus == ModbusRegisters.STATUS_AUTORUN; }
    public boolean isIdle()       { return machineStatus == ModbusRegisters.STATUS_IDLE
                                          || machineStatus == ModbusRegisters.STATUS_POWER_ON; }
    public boolean hasAlarm()     { return (errorStatus & 0x01) != 0; }
    public boolean hasWarning()   { return (errorStatus & 0x02) != 0; }
    public boolean isDoorLocked() { return doorStatus == ModbusRegisters.DOOR_LOCKED; }

    public int remainingMinutes() { return remainingHour * 60 + remainingMin; }
}
