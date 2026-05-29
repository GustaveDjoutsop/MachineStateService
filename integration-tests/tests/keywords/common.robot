*** Settings ***
Library     RequestsLibrary
Library     JSONLibrary
Resource    ../resources/variables.robot

*** Keywords ***
Create Session To Service
    [Documentation]    Opens an HTTP session to the MachineStateService
    Create Session    machine    ${BASE_URL}    verify=False

Delete Session To Service
    Delete All Sessions

Get All Machines
    [Documentation]    Returns the full machine summary response
    ${resp}=    GET On Session    machine    /api/machines    expected_status=200
    RETURN    ${resp.json()}

Get Machine By Id
    [Documentation]    Returns single machine status response
    [Arguments]    ${machine_id}
    ${resp}=    GET On Session    machine    /api/machines/${machine_id}    expected_status=200
    RETURN    ${resp.json()}

Get Machine By Id Expecting Error
    [Arguments]    ${machine_id}    ${expected_status}
    ${resp}=    GET On Session    machine    /api/machines/${machine_id}
    ...    expected_status=${expected_status}
    RETURN    ${resp.json()}

Start Cycle
    [Documentation]    Posts a start-cycle request and returns the cycle record
    [Arguments]    ${machine_id}    ${cycle_type}=${CYCLE_TYPE_NORMAL}
    ...            ${duration}=${DURATION_30}    ${pulse_count}=${PULSE_1}
    ...            ${rfid_uid}=${RFID_UID}    ${tx_ref}=${TX_REF}
    &{body}=    Create Dictionary
    ...    machineId=${machine_id}
    ...    cycleType=${cycle_type}
    ...    durationMinutes=${duration}
    ...    pulseCount=${pulse_count}
    ...    rfidCardUid=${rfid_uid}
    ...    transactionReference=${tx_ref}
    ${resp}=    POST On Session    machine    /api/machines/start-cycle    json=${body}    expected_status=200
    RETURN    ${resp.json()}

Start Cycle Expecting Error
    [Arguments]    ${machine_id}    ${expected_status}
    &{body}=    Create Dictionary
    ...    machineId=${machine_id}
    ...    cycleType=${CYCLE_TYPE_NORMAL}
    ...    durationMinutes=${DURATION_30}
    ...    pulseCount=${PULSE_1}
    ${resp}=    POST On Session    machine    /api/machines/start-cycle
    ...    json=${body}    expected_status=${expected_status}
    RETURN    ${resp.json()}

Post Telemetry
    [Documentation]    Sends HTTP telemetry as an ESP32 would
    [Arguments]    ${machine_id}    ${status}    ${cycle_type}=NONE    ${progress}=${0}
    ...            ${temperature}=${None}    ${door_locked}=${False}
    &{body}=    Create Dictionary
    ...    machineId=${machine_id}
    ...    status=${status}
    ...    cycleType=${cycle_type}
    ...    cycleProgress=${progress}
    ...    doorLocked=${door_locked}
    IF    $temperature is not None
        Set To Dictionary    ${body}    temperature=${temperature}
    END
    ${resp}=    POST On Session    machine    /api/esp32/telemetry    json=${body}    expected_status=200
    RETURN    ${resp.json()}

Get Machine Events
    [Arguments]    ${machine_id}
    ${resp}=    GET On Session    machine    /api/machines/${machine_id}/events    expected_status=200
    RETURN    ${resp.json()}

Get Machine Cycles
    [Arguments]    ${machine_id}
    ${resp}=    GET On Session    machine    /api/machines/${machine_id}/cycles    expected_status=200
    RETURN    ${resp.json()}

Get Mqtt Status
    ${resp}=    GET On Session    machine    /api/esp32/mqtt/status    expected_status=200
    RETURN    ${resp.json()}
