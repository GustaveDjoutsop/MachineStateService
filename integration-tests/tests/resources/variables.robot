*** Variables ***
${BASE_URL}             http://localhost:8082
${CONTENT_TYPE}         application/json

# Machine IDs expected after auto-seeding
@{WASHER_IDS}           washer_01    washer_02    washer_03    washer_04    washer_05    washer_06
@{DRYER_IDS}            dryer_01    dryer_02    dryer_03    dryer_04

${MACHINE_WASHER}       washer_01
${MACHINE_WASHER_2}     washer_02
${MACHINE_DRYER}        dryer_01
${MACHINE_UNKNOWN}      unknown_99

# Cycle data
${CYCLE_TYPE_NORMAL}    NORMAL
${CYCLE_TYPE_COTTON}    COTTON_60
${DURATION_30}          ${30}
${DURATION_60}          ${60}
${PULSE_1}              ${1}
${PULSE_2}              ${2}
${RFID_UID}             UID-MACHINE-TEST-001
${TX_REF}               txn-integration-001

# Telemetry
${TELEMETRY_STATUS_RUNNING}     RUNNING
${TELEMETRY_STATUS_IDLE}        IDLE
${TELEMETRY_TEMP}               ${60.0}
${TELEMETRY_PROGRESS}           ${50}
