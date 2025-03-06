/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.motionblinds.dto;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

/**
 * The {@link CurtainMotor} is model of device
 * handlers.
 *
 * @author Petr Shatsillo - Initial contribution
 */
@NonNullByDefault
public class CurtainMotor {
    private final Logger logger = LoggerFactory.getLogger(CurtainMotor.class);
    String mac = "";
    String deviceType = "";
    int controlMode = -1;
    int rssi = 0;
    int currentPosition = -1;
    int targetPosition = -1;
    int currentState = -1;
    int operation = -1;
    int switchMode = -1;
    int direction = -1;

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public int getControlMode() {
        return controlMode;
    }

    public void setControlMode(int controlMode) {
        this.controlMode = controlMode;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }

    public int getTargetPosition() {
        return targetPosition;
    }

    public void setTargetPosition(int targetPosition) {
        this.targetPosition = targetPosition;
    }

    public int getCurrentState() {
        return currentState;
    }

    public void setCurrentState(int currentState) {
        this.currentState = currentState;
    }

    public int getOperation() {
        return operation;
    }

    public void setOperation(int operation) {
        this.operation = operation;
    }

    public int getSwitchMode() {
        return switchMode;
    }

    public void setSwitchMode(int switchMode) {
        this.switchMode = switchMode;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public void heartBeat(JsonObject data) {
        try {
            logger.debug("receive: {}", data);
            if (data.get("mac") != null) {
                mac = data.get("mac").getAsString();
            }
            if (data.get("deviceType") != null) {
                deviceType = data.get("deviceType").getAsString();
            }
            if (data.getAsJsonObject("data") != null) {
                JsonObject dataObj = data.getAsJsonObject("data");
                if (dataObj.get("controlMode") != null) {
                    controlMode = dataObj.get("controlMode").getAsInt();
                    logger.debug("controlMode: {}", controlMode);
                }
                if (dataObj.get("RSSI") != null) {
                    rssi = dataObj.get("RSSI").getAsInt();
                    logger.debug("rssi: {}", rssi);
                }
                if (dataObj.get("currentPosition") != null) {
                    currentPosition = dataObj.get("currentPosition").getAsInt();
                    logger.debug("currentPosition: {}", currentPosition);
                }
                if (dataObj.get("targetPosition") != null) {
                    targetPosition = dataObj.get("targetPosition").getAsInt();
                    logger.debug("targetPosition: {}", targetPosition);
                }
                if (dataObj.get("currentState") != null) {
                    currentState = dataObj.get("currentState").getAsInt();
                    logger.debug("currentState: {}", currentState);
                }
                if (dataObj.get("operation") != null) {
                    operation = dataObj.get("operation").getAsInt();
                    logger.debug("operation: {}", operation);
                }
                if (dataObj.get("switchMode") != null) {
                    switchMode = dataObj.get("switchMode").getAsInt();
                    logger.debug("switchMode: {}", switchMode);
                }
                if (dataObj.get("direction") != null) {
                    direction = dataObj.get("direction").getAsInt();
                    logger.debug("direction: {}", direction);
                }
            }
        } catch (Exception e) {
            logger.error("Curtain JSON read error: {} in data {}", e.getLocalizedMessage(), data);
        }
        // 12:02:03.977 DEBUG org.openhab.binding.motionblinds.dto.CurtainMotor receive:
        // {"msgType":"Heartbeat","mac":"483fda1eb16e","deviceType":"22000000","token":"37412C478E0FBEAB","data":{"operation":2,"direction":1,"currentPosition":99,"targetPosition":100,"currentState":3,"switchMode":0,"controlMode":0,"RSSI":-52}}
    }
}
