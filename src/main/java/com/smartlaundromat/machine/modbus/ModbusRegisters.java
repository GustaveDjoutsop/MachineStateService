package com.smartlaundromat.machine.modbus;

/**
 * Modbus RTU register map for the SX174003A washing-machine controller.
 *
 * <p>Source: <em>SX174003A communication protocol-20260113.xlsx</em> (Protocol description sheet).
 *
 * <h2>Bus parameters</h2>
 * <ul>
 *   <li>Baud rate 9600, 8 data bits, no parity, 1 stop bit (9600/8/N/1)</li>
 *   <li>Checksum: CRC16 (Modbus)</li>
 *   <li>Terminal = master, washing machine = slave (address 1–247)</li>
 * </ul>
 *
 * <h2>Register addressing</h2>
 * The spreadsheet documents PLC-style addresses (e.g. {@code 4X1145}). The value on the
 * wire is the zero-based protocol address, i.e. {@code PLC - 1}. For example {@code 4X1145}
 * → {@code 0x0478} (verified against the sample frame {@code 01 10 04 78 00 01 02 00 01 28 28}).
 */
public final class ModbusRegisters {

    private ModbusRegisters() { }

    // ── Function codes ──────────────────────────────────────────────────────────
    public static final int FUNC_READ_HOLDING   = 0x03; // Terminal → machine: read register
    public static final int FUNC_WRITE_MULTIPLE  = 0x10; // Terminal → machine: write register

    // ── Write registers (function 0x10) ─────────────────────────────────────────
    /** 4X1145 — Reset alarm and silence (0–1). */
    public static final int REG_RESET_ALARM       = 0x0478;
    /** 4X1146 — Start the machine (0–1). */
    public static final int REG_START             = 0x0479;
    /** 4X1147 — Perform the next step while running (0–1). */
    public static final int REG_NEXT_STEP         = 0x047A;
    /** 4X1148 — Forced stop (0–1). */
    public static final int REG_FORCED_STOP       = 0x047B;
    /** 4X1149 — Input the number of coins (0–65535). */
    public static final int REG_INPUT_COINS       = 0x047C;
    /** 4X1150 — Select program number (0–3). */
    public static final int REG_SELECT_PROGRAM    = 0x047D;
    /** 4X1151 — Save parameters (0–1). */
    public static final int REG_SAVE_PARAMS       = 0x047E;
    /** 4X1152 — Save program data (0–1). */
    public static final int REG_SAVE_PROGRAM_DATA = 0x047F;
    /** 4X1153 — Send the number of the automatic program to be read (1–3). */
    public static final int REG_READ_AUTO_PROGRAM = 0x0480;

    // ── Read registers (function 0x03) ──────────────────────────────────────────
    /** 5X1165 — Read washing-machine monitor data block. Length 20 registers. */
    public static final int REG_MONITOR_DATA      = 0x048C;
    public static final int MONITOR_DATA_LENGTH   = 20;

    /** 5X1205 — Read alarms/warnings and input/output status. Length 7 registers. */
    public static final int REG_ALARM_IO          = 0x04B4;
    public static final int ALARM_IO_LENGTH       = 7;

    // ── Monitor-data block: zero-based register offsets within REG_MONITOR_DATA ──
    public static final int IDX_MACHINE_STATUS    = 0;  // 0:PowerOn 1:Idle 3:Autorun
    public static final int IDX_DOOR_STATUS       = 1;  // 0:Idle 1:Open 2:Closed 3:Locked ...
    public static final int IDX_ERROR_STATUS      = 2;  // bit0:Alarm bit1:Warning
    public static final int IDX_STEP_TIME_MIN     = 3;
    public static final int IDX_STEP_TIME_SEC     = 4;
    public static final int IDX_REMAIN_HOUR       = 5;
    public static final int IDX_REMAIN_MIN        = 6;
    public static final int IDX_REMAIN_SEC        = 7;
    public static final int IDX_WATER_LEVEL       = 8;  // cm
    public static final int IDX_WATER_LEVEL_SET   = 9;  // cm
    public static final int IDX_TEMPERATURE       = 10; // ℃
    public static final int IDX_TEMPERATURE_SET   = 11; // ℃
    public static final int IDX_SPEED             = 12; // rpm
    public static final int IDX_SPEED_SET         = 13; // rpm
    public static final int IDX_PROGRAM_NUMBER    = 14;
    public static final int IDX_OPERATION_STEP    = 15;
    public static final int IDX_COINS_REQUIRED    = 16;
    public static final int IDX_COINS_CURRENT     = 17;
    public static final int IDX_TOTAL_COINS       = 18;
    public static final int IDX_COINBOX_COINS     = 19;

    // ── Machine-status enum values (IDX_MACHINE_STATUS) ─────────────────────────
    public static final int STATUS_POWER_ON       = 0;
    public static final int STATUS_IDLE           = 1;
    public static final int STATUS_AUTORUN        = 3;

    // ── Door-status enum values (IDX_DOOR_STATUS) ───────────────────────────────
    public static final int DOOR_IDLE             = 0;
    public static final int DOOR_OPENED           = 1;
    public static final int DOOR_CLOSED           = 2;
    public static final int DOOR_LOCKED           = 3;
    public static final int DOOR_ERROR            = 4;
}
