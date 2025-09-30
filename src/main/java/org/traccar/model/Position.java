/*
 * Copyright 2012 - 2024 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.model;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.traccar.storage.QueryIgnore;
import org.traccar.storage.StorageName;

@StorageName("tc_positions")
public class Position extends Message {

    public static final String KEY_ORIGINAL = "raw";
    public static final String KEY_INDEX = "index";
    public static final String KEY_HDOP = "hdop";
    public static final String KEY_VDOP = "vdop";
    public static final String KEY_PDOP = "pdop";
    public static final String KEY_SATELLITES = "sat"; // in use
    public static final String KEY_SATELLITES_VISIBLE = "satVisible";
    public static final String KEY_RSSI = "rssi";
    public static final String KEY_GPS = "gps";
    public static final String KEY_ROAMING = "roaming";
    public static final String KEY_EVENT = "event";
    public static final String KEY_ALARM = "alarm";
    public static final String KEY_STATUS = "status";
    public static final String KEY_ODOMETER = "odometer"; // meters
    public static final String KEY_ODOMETER_SERVICE = "serviceOdometer"; // meters
    public static final String KEY_ODOMETER_TRIP = "tripOdometer"; // meters
    public static final String KEY_HOURS = "hours"; // milliseconds
    public static final String KEY_STEPS = "steps";
    public static final String KEY_HEART_RATE = "heartRate";
    public static final String KEY_INPUT = "input";
    public static final String KEY_OUTPUT = "output";
    public static final String KEY_IMAGE = "image";
    public static final String KEY_VIDEO = "video";
    public static final String KEY_AUDIO = "audio";

    // The units for the below four KEYs currently vary.
    // The preferred units of measure are specified in the comment for each.
    public static final String KEY_POWER = "power"; // volts
    public static final String KEY_BATTERY = "battery"; // volts
    public static final String KEY_BATTERY_LEVEL = "batteryLevel"; // percentage
    public static final String KEY_FUEL = "fuel"; // liters
    public static final String KEY_FUEL_USED = "fuelUsed"; // liters
    public static final String KEY_FUEL_CONSUMPTION = "fuelConsumption"; // liters/hour
    public static final String KEY_FUEL_LEVEL = "fuelLevel"; // percentage

    public static final String KEY_VERSION_FW = "versionFw";
    public static final String KEY_VERSION_HW = "versionHw";
    public static final String KEY_TYPE = "type";
    public static final String KEY_IGNITION = "ignition";
    public static final String KEY_FLAGS = "flags";
    public static final String KEY_ANTENNA = "antenna";
    public static final String KEY_CHARGE = "charge";
    public static final String KEY_IP = "ip";
    public static final String KEY_ARCHIVE = "archive";
    public static final String KEY_DISTANCE = "distance"; // meters
    public static final String KEY_TOTAL_DISTANCE = "totalDistance"; // meters
    public static final String KEY_RPM = "rpm";
    public static final String KEY_VIN = "vin";
    public static final String KEY_APPROXIMATE = "approximate";
    public static final String KEY_THROTTLE = "throttle";
    public static final String KEY_MOTION = "motion";
    public static final String KEY_ARMED = "armed";
    public static final String KEY_GEOFENCE = "geofence";
    public static final String KEY_ACCELERATION = "acceleration";
    public static final String KEY_HUMIDITY = "humidity";
    public static final String KEY_DEVICE_TEMP = "deviceTemp"; // celsius
    public static final String KEY_COOLANT_TEMP = "coolantTemp"; // celsius
    public static final String KEY_ENGINE_LOAD = "engineLoad";
    public static final String KEY_ENGINE_TEMP = "engineTemp";
    public static final String KEY_OPERATOR = "operator";
    public static final String KEY_COMMAND = "command";
    public static final String KEY_BLOCKED = "blocked";
    public static final String KEY_LOCK = "lock";
    public static final String KEY_DOOR = "door";
    public static final String KEY_AXLE_WEIGHT = "axleWeight";
    public static final String KEY_G_SENSOR = "gSensor";
    public static final String KEY_ICCID = "iccid";
    public static final String KEY_PHONE = "phone";
    public static final String KEY_SPEED_LIMIT = "speedLimit";
    public static final String KEY_DRIVING_TIME = "drivingTime";

    public static final String KEY_DTCS = "dtcs";
    public static final String KEY_OBD_SPEED = "obdSpeed"; // km/h
    public static final String KEY_OBD_ODOMETER = "obdOdometer"; // meters

    public static final String KEY_RESULT = "result";

    public static final String KEY_DRIVER_UNIQUE_ID = "driverUniqueId";
    public static final String KEY_CARD = "card";

    // Start with 1 not 0
    public static final String PREFIX_TEMP = "temp";
    public static final String PREFIX_ADC = "adc";
    public static final String PREFIX_IO = "io";
    public static final String PREFIX_COUNT = "count";
    public static final String PREFIX_IN = "in";
    public static final String PREFIX_OUT = "out";

    public static final String ALARM_GENERAL = "general";
    public static final String ALARM_SOS = "sos";
    public static final String ALARM_VIBRATION = "vibration";
    public static final String ALARM_MOVEMENT = "movement";
    public static final String ALARM_LOW_SPEED = "lowspeed";
    public static final String ALARM_OVERSPEED = "overspeed";
    public static final String ALARM_FALL_DOWN = "fallDown";
    public static final String ALARM_LOW_POWER = "lowPower";
    public static final String ALARM_LOW_BATTERY = "lowBattery";
    public static final String ALARM_FAULT = "fault";
    public static final String ALARM_POWER_OFF = "powerOff";
    public static final String ALARM_POWER_ON = "powerOn";
    public static final String ALARM_DOOR = "door";
    public static final String ALARM_LOCK = "lock";
    public static final String ALARM_UNLOCK = "unlock";
    public static final String ALARM_GEOFENCE = "geofence";
    public static final String ALARM_GEOFENCE_ENTER = "geofenceEnter";
    public static final String ALARM_GEOFENCE_EXIT = "geofenceExit";
    public static final String ALARM_GPS_ANTENNA_CUT = "gpsAntennaCut";
    public static final String ALARM_ACCIDENT = "accident";
    public static final String ALARM_TOW = "tow";
    public static final String ALARM_IDLE = "idle";
    public static final String ALARM_HIGH_RPM = "highRpm";
    public static final String ALARM_ACCELERATION = "hardAcceleration";
    public static final String ALARM_BRAKING = "hardBraking";
    public static final String ALARM_CORNERING = "hardCornering";
    public static final String ALARM_LANE_CHANGE = "laneChange";
    public static final String ALARM_FATIGUE_DRIVING = "fatigueDriving";
    public static final String ALARM_POWER_CUT = "powerCut";
    public static final String ALARM_POWER_RESTORED = "powerRestored";
    public static final String ALARM_JAMMING = "jamming";
    public static final String ALARM_TEMPERATURE = "temperature";
    public static final String ALARM_PARKING = "parking";
    public static final String ALARM_BONNET = "bonnet";
    public static final String ALARM_FOOT_BRAKE = "footBrake";
    public static final String ALARM_FUEL_LEAK = "fuelLeak";
    public static final String ALARM_TAMPERING = "tampering";
    public static final String ALARM_REMOVING = "removing";
    // Teltonika specific keys
    // FMB140
    public static final String KEY_GSM_SIGNAL = "gsmSignal";
    public static final String KEY_ANALOG1 = "analog1";
    public static final String KEY_NUM_DTC = "numDtc";
    public static final String KEY_DATA_MODE = "dataMode";
    public static final String KEY_SLEEP_MODE = "sleepMode";
    public static final String KEY_GNSS_STATUS = "gnssStatus";
    public static final String KEY_GSM_CELL_ID = "gsmCellId";
    public static final String KEY_GSM_AREA_CODE = "gsmAreaCode";
    public static final String KEY_BATTERY_CURRENT = "batteryCurrent";
    public static final String KEY_INPUT1 = "input1";
    public static final String KEY_OUTPUT1 = "output1";
    // Teltonika specific keys (batch 2)
    public static final String KEY_FUEL_USED_GPS = "fuelUsedGps";
    public static final String KEY_FUEL_RATE_GPS = "fuelRateGps";
    public static final String KEY_ICCID1 = "iccid1";
    public static final String KEY_SD_STATUS = "sdStatus";
    public static final String KEY_INPUT2 = "input2";
    public static final String KEY_INPUT3 = "input3";
    public static final String KEY_ANALOG_INPUT2 = "analogInput2";
    public static final String KEY_OUTPUT2 = "output2";
    public static final String KEY_DALLAS_TEMP1 = "dallasTemp1";
    public static final String KEY_DALLAS_TEMP2 = "dallasTemp2";
    public static final String KEY_DALLAS_TEMP3 = "dallasTemp3";
    public static final String KEY_DALLAS_TEMP4 = "dallasTemp4";
    public static final String KEY_DALLAS_TEMP_ID1 = "dallasTempId1";
    public static final String KEY_DALLAS_TEMP_ID2 = "dallasTempId2";
    public static final String KEY_DALLAS_TEMP_ID3 = "dallasTempId3";
    public static final String KEY_DALLAS_TEMP_ID4 = "dallasTempId4";
    public static final String KEY_IBUTTON = "ibutton";
    public static final String KEY_RFID = "rfid";
    public static final String KEY_LLS1_FUEL = "lls1Fuel";
    public static final String KEY_LLS1_TEMP = "lls1Temp";
    public static final String KEY_LLS2_FUEL = "lls2Fuel";
    public static final String KEY_LLS2_TEMP = "lls2Temp";
    public static final String KEY_LLS3_FUEL = "lls3Fuel";
    public static final String KEY_LLS3_TEMP = "lls3Temp";
    public static final String KEY_LLS4_FUEL = "lls4Fuel";
    public static final String KEY_LLS4_TEMP = "lls4Temp";
    public static final String KEY_LLS5_FUEL = "lls5Fuel";
    public static final String KEY_LLS5_TEMP = "lls5Temp";
    public static final String KEY_ECO_SCORE = "ecoScore";
    public static final String KEY_USER_ID = "userId";
    public static final String KEY_NETWORK_TYPE = "networkType";
    public static final String KEY_PULSE_COUNT1 = "pulseCount1";
    public static final String KEY_PULSE_COUNT2 = "pulseCount2";
    public static final String KEY_BT_STATUS = "btStatus";
    public static final String KEY_BARCODE_ID = "barcodeId";
    public static final String KEY_INSTANT_MOVEMENT = "instantMovement";
    public static final String KEY_UL202_FUEL = "ul202Fuel";
    public static final String KEY_UL202_STATUS = "ul202Status";
    public static final String KEY_OUTPUT3 = "output3";
    public static final String KEY_GROUND_SENSE = "groundSense";
    public static final String KEY_ISO6709 = "iso6709";
    public static final String KEY_CELL_ID = "cellId";
    public static final String KEY_DRIVER_NAME = "driverName";
    public static final String KEY_DRIVER_LICENSE_TYPE = "driverLicenseType";
    public static final String KEY_DRIVER_GENDER = "driverGender";
    public static final String KEY_DRIVER_CARD_ID = "driverCardId";
    public static final String KEY_DRIVER_CARD_EXPIRY = "driverCardExpiry";
    public static final String KEY_DRIVER_ISSUE_PLACE = "driverIssuePlace";
    public static final String KEY_DRIVER_STATUS_EVENT = "driverStatusEvent";
    public static final String KEY_AIN_SPEED = "ainSpeed";
    public static final String KEY_VENDOR_NAME = "vendorName";
    public static final String KEY_VEHICLE_NUMBER = "vehicleNumber";
    public static final String KEY_SPEED_SENSOR_STATUS = "speedSensorStatus";
    public static final String KEY_WAKE_REASON = "wakeReason";
    public static final String KEY_EYE_HUM1 = "eyeHum1";
    public static final String KEY_EYE_HUM2 = "eyeHum2";
    public static final String KEY_EYE_HUM3 = "eyeHum3";
    public static final String KEY_EYE_HUM4 = "eyeHum4";
    public static final String KEY_EYE_MAG1 = "eyeMag1";
    public static final String KEY_EYE_MAG2 = "eyeMag2";
    public static final String KEY_EYE_MAG3 = "eyeMag3";
    public static final String KEY_EYE_MAG4 = "eyeMag4";
    public static final String KEY_EYE_MOVE1 = "eyeMove1";
    public static final String KEY_EYE_MOVE2 = "eyeMove2";
    public static final String KEY_EYE_MOVE3 = "eyeMove3";
    public static final String KEY_EYE_MOVE4 = "eyeMove4";
    public static final String KEY_EYE_PITCH1 = "eyePitch1";
    public static final String KEY_EYE_PITCH2 = "eyePitch2";
    public static final String KEY_EYE_PITCH3 = "eyePitch3";
    public static final String KEY_EYE_PITCH4 = "eyePitch4";
    public static final String KEY_EYE_LOW_BAT1 = "eyeLowBat1";
    public static final String KEY_EYE_LOW_BAT2 = "eyeLowBat2";
    public static final String KEY_EYE_LOW_BAT3 = "eyeLowBat3";
    public static final String KEY_EYE_LOW_BAT4 = "eyeLowBat4";
    public static final String KEY_EYE_BAT_VOLT1 = "eyeBatVolt1";
    public static final String KEY_EYE_BAT_VOLT2 = "eyeBatVolt2";
    public static final String KEY_EYE_BAT_VOLT3 = "eyeBatVolt3";
    public static final String KEY_EYE_BAT_VOLT4 = "eyeBatVolt4";
    public static final String KEY_EYE_MOVE_COUNT1 = "eyeMoveCount1";
    public static final String KEY_EYE_MOVE_COUNT2 = "eyeMoveCount2";
    public static final String KEY_EYE_MOVE_COUNT3 = "eyeMoveCount3";
    public static final String KEY_EYE_MOVE_COUNT4 = "eyeMoveCount4";
    public static final String KEY_EYE_MAG_COUNT1 = "eyeMagCount1";
    public static final String KEY_EYE_MAG_COUNT2 = "eyeMagCount2";
    public static final String KEY_EYE_MAG_COUNT3 = "eyeMagCount3";
    public static final String KEY_EYE_MAG_COUNT4 = "eyeMagCount4";
    public static final String KEY_AXL_CALIB_STATUS = "axlCalibStatus";
    public static final String KEY_BLE_RFID1 = "bleRfid1";
    public static final String KEY_BLE_RFID2 = "bleRfid2";
    public static final String KEY_BLE_RFID3 = "bleRfid3";
    public static final String KEY_BLE_RFID4 = "bleRfid4";
    public static final String KEY_BLE_BTN1_STATE1 = "bleBtn1State1";
    public static final String KEY_BLE_BTN1_STATE2 = "bleBtn1State2";
    public static final String KEY_BLE_BTN1_STATE3 = "bleBtn1State3";
    public static final String KEY_BLE_BTN1_STATE4 = "bleBtn1State4";
    public static final String KEY_BLE_BTN2_STATE1 = "bleBtn2State1";
    public static final String KEY_BLE_BTN2_STATE2 = "bleBtn2State2";
    public static final String KEY_BLE_BTN2_STATE3 = "bleBtn2State3";
    public static final String KEY_BLE_BTN2_STATE4 = "bleBtn2State4";
    public static final String KEY_FREQ_DIN1 = "freqDin1";
    public static final String KEY_FREQ_DIN2 = "freqDin2";
    public static final String KEY_CONNECTIVITY_QUALITY = "connectivityQuality";
    // Geofence zones
    public static final String KEY_GEOFENCE_ZONE_01 = "geofenceZone01";
    public static final String KEY_GEOFENCE_ZONE_02 = "geofenceZone02";
    public static final String KEY_GEOFENCE_ZONE_03 = "geofenceZone03";
    public static final String KEY_GEOFENCE_ZONE_04 = "geofenceZone04";
    public static final String KEY_GEOFENCE_ZONE_05 = "geofenceZone05";
    public static final String KEY_GEOFENCE_ZONE_06 = "geofenceZone06";
    public static final String KEY_GEOFENCE_ZONE_07 = "geofenceZone07";
    public static final String KEY_GEOFENCE_ZONE_08 = "geofenceZone08";
    public static final String KEY_GEOFENCE_ZONE_09 = "geofenceZone09";
    public static final String KEY_GEOFENCE_ZONE_10 = "geofenceZone10";
    public static final String KEY_GEOFENCE_ZONE_11 = "geofenceZone11";
    public static final String KEY_GEOFENCE_ZONE_12 = "geofenceZone12";
    public static final String KEY_GEOFENCE_ZONE_13 = "geofenceZone13";
    public static final String KEY_GEOFENCE_ZONE_14 = "geofenceZone14";
    public static final String KEY_GEOFENCE_ZONE_15 = "geofenceZone15";
    public static final String KEY_GEOFENCE_ZONE_16 = "geofenceZone16";
    public static final String KEY_GEOFENCE_ZONE_17 = "geofenceZone17";
    public static final String KEY_GEOFENCE_ZONE_18 = "geofenceZone18";
    public static final String KEY_GEOFENCE_ZONE_19 = "geofenceZone19";
    public static final String KEY_GEOFENCE_ZONE_20 = "geofenceZone20";
    public static final String KEY_GEOFENCE_ZONE_21 = "geofenceZone21";
    public static final String KEY_GEOFENCE_ZONE_22 = "geofenceZone22";
    public static final String KEY_GEOFENCE_ZONE_23 = "geofenceZone23";
    public static final String KEY_GEOFENCE_ZONE_24 = "geofenceZone24";
    public static final String KEY_GEOFENCE_ZONE_25 = "geofenceZone25";
    public static final String KEY_GEOFENCE_ZONE_26 = "geofenceZone26";
    public static final String KEY_GEOFENCE_ZONE_27 = "geofenceZone27";
    public static final String KEY_GEOFENCE_ZONE_28 = "geofenceZone28";
    public static final String KEY_GEOFENCE_ZONE_29 = "geofenceZone29";
    public static final String KEY_GEOFENCE_ZONE_30 = "geofenceZone30";
    public static final String KEY_GEOFENCE_ZONE_31 = "geofenceZone31";
    public static final String KEY_GEOFENCE_ZONE_32 = "geofenceZone32";
    public static final String KEY_GEOFENCE_ZONE_33 = "geofenceZone33";
    public static final String KEY_GEOFENCE_ZONE_34 = "geofenceZone34";
    public static final String KEY_GEOFENCE_ZONE_35 = "geofenceZone35";
    public static final String KEY_GEOFENCE_ZONE_36 = "geofenceZone36";
    public static final String KEY_GEOFENCE_ZONE_37 = "geofenceZone37";
    public static final String KEY_GEOFENCE_ZONE_38 = "geofenceZone38";
    public static final String KEY_GEOFENCE_ZONE_39 = "geofenceZone39";
    public static final String KEY_GEOFENCE_ZONE_40 = "geofenceZone40";
    public static final String KEY_GEOFENCE_ZONE_41 = "geofenceZone41";
    public static final String KEY_GEOFENCE_ZONE_42 = "geofenceZone42";
    public static final String KEY_GEOFENCE_ZONE_43 = "geofenceZone43";
    public static final String KEY_GEOFENCE_ZONE_44 = "geofenceZone44";
    public static final String KEY_GEOFENCE_ZONE_45 = "geofenceZone45";
    public static final String KEY_GEOFENCE_ZONE_46 = "geofenceZone46";
    public static final String KEY_GEOFENCE_ZONE_47 = "geofenceZone47";
    public static final String KEY_GEOFENCE_ZONE_48 = "geofenceZone48";
    public static final String KEY_GEOFENCE_ZONE_49 = "geofenceZone49";
    public static final String KEY_GEOFENCE_ZONE_50 = "geofenceZone50";
    public static final String KEY_AUTO_GEOFENCE = "autoGeofence";
    public static final String KEY_TRIP = "trip";
    public static final String KEY_OVERSPEED = "overspeed";
    public static final String KEY_CRASH_TRACE = "crashTrace";
    public static final String KEY_ALCOHOL = "alcoholContent";
    public static final String KEY_GREEN_TYPE = "greenDrivingType";
    public static final String KEY_CRASH_DETECTION = "crashDetection";
    public static final String KEY_IMMOBILIZER = "immobilizer";
    public static final String KEY_GREEN_VALUE = "greenDrivingValue";
    public static final String KEY_ICCID2 = "iccid2";
    public static final String KEY_GREEN_DURATION = "greenDrivingDuration";
    public static final String KEY_ECO_MAX = "ecoMax";
    public static final String KEY_ECO_AVG = "ecoAverage";
    public static final String KEY_ECO_DURATION = "ecoDuration";
    public static final String KEY_DRIVING_STATE = "drivingState";
    public static final String KEY_DRIVING_RECORDS = "drivingRecords";
    public static final String KEY_CRASH_COUNT = "crashEventCounter";
    public static final String KEY_GNSS_JAMMING = "gnssJamming";
    public static final String KEY_PRIVATE_MODE = "privateMode";
    public static final String KEY_IGNITION_ON_TIME = "ignitionOnTime";
    public static final String KEY_MOTO_FALL = "motorcycleFall";

    // public static final String KEY_VIN = "vin";
    public static final String KEY_DTC_COUNT = "dtcCount";
    public static final String KEY_FUEL_PRESSURE = "fuelPressure";
    public static final String KEY_MAP = "intakeMap";
    public static final String KEY_TIMING_ADVANCE = "timingAdvance";
    public static final String KEY_INTAKE_TEMP = "intakeTemp";
    public static final String KEY_RUNTIME = "runtime";
    public static final String KEY_MAF = "maf";
    public static final String KEY_REL_FUEL_PRESSURE = "relFuelPressure";
    public static final String KEY_DIR_FUEL_PRESSURE = "dirFuelPressure";
    public static final String KEY_CMD_EGR = "cmdEgr";
    public static final String KEY_EGR_ERROR = "egrError";
    public static final String KEY_DIST_CLEAR = "distSinceClear";
    public static final String KEY_BARO_PRESSURE = "baroPressure";
    public static final String KEY_MODULE_VOLTAGE = "moduleVoltage";
    public static final String KEY_EXTERNAL_VOLTAGE = "externalVoltage";
    public static final String KEY_ABS_LOAD = "absoluteLoad";
    public static final String KEY_FUEL_TYPE = "fuelType";
    public static final String KEY_AMBIENT_AIR_TEMP = "ambientAirTemp";
    public static final String KEY_MIL_RUNTIME = "milRuntime";
    public static final String KEY_TIME_SINCE_CLEAR = "timeSinceCodesClear";
    public static final String KEY_FUEL_RAIL_PRESS = "fuelRailPressure";
    public static final String KEY_ENGINE_OIL_TEMP = "engineOilTemp";
    public static final String KEY_INJECTION_TIMING = "injectionTiming";
    public static final String KEY_THROTTLE_POS_GROUP = "throttlePosGroup";
    public static final String KEY_EQUIV_RATIO = "equivRatio";
    public static final String KEY_MAP2 = "intakeMap2";
    public static final String KEY_HYBRID_VOLTAGE = "hybridVoltage";
    public static final String KEY_HYBRID_CURRENT = "hybridCurrent";
    public static final String KEY_FUEL_RATE = "fuelRate";
    public static final String KEY_OEM_TOTAL_MILEAGE = "oemTotalMileage";
    public static final String KEY_OEM_FUEL_LEVEL = "oemFuelLevel";
    public static final String KEY_OEM_DISTANCE_SERVICE = "oemDistanceService";
    public static final String KEY_OEM_BATTERY_CHARGE = "oemBatteryCharge";
    public static final String KEY_OEM_REMAINING_DIST = "oemRemainingDist";
    public static final String KEY_OEM_BATTERY_HEALTH = "oemBatteryHealth";
    public static final String KEY_OEM_BATTERY_TEMP = "oemBatteryTemp";
    public static final String KEY_BLE_BATTERY1 = "bleBattery1";
    public static final String KEY_BLE_BATTERY2 = "bleBattery2";
    public static final String KEY_BLE_BATTERY3 = "bleBattery3";
    // BLE Battery
    public static final String KEY_BLE_BATTERY4 = "bleBattery4";

    // BLE Humidity
    public static final String KEY_BLE_HUMIDITY1 = "bleHumidity1";
    public static final String KEY_BLE_HUMIDITY2 = "bleHumidity2";
    public static final String KEY_BLE_HUMIDITY3 = "bleHumidity3";
    public static final String KEY_BLE_HUMIDITY4 = "bleHumidity4";

    // BLE Fuel Level
    public static final String KEY_BLE_FUEL_LEVEL1 = "bleFuelLevel1";
    public static final String KEY_BLE_FUEL_LEVEL2 = "bleFuelLevel2";
    public static final String KEY_BLE_FUEL_LEVEL3 = "bleFuelLevel3";
    public static final String KEY_BLE_FUEL_LEVEL4 = "bleFuelLevel4";

    // BLE Fuel Frequency
    public static final String KEY_BLE_FUEL_FREQ1 = "bleFuelFreq1";
    public static final String KEY_BLE_FUEL_FREQ2 = "bleFuelFreq2";
    public static final String KEY_BLE_FUEL_FREQ3 = "bleFuelFreq3";
    public static final String KEY_BLE_FUEL_FREQ4 = "bleFuelFreq4";

    // BLE Luminosity
    public static final String KEY_BLE_LUMINOSITY1 = "bleLuminosity1";
    public static final String KEY_BLE_LUMINOSITY2 = "bleLuminosity2";
    public static final String KEY_BLE_LUMINOSITY3 = "bleLuminosity3";
    public static final String KEY_BLE_LUMINOSITY4 = "bleLuminosity4";

    // BLE Custom 1
    public static final String KEY_BLE1_CUSTOM1 = "ble1Custom1";
    public static final String KEY_BLE1_CUSTOM2 = "ble1Custom2";
    public static final String KEY_BLE1_CUSTOM3 = "ble1Custom3";
    public static final String KEY_BLE1_CUSTOM4 = "ble1Custom4";
    public static final String KEY_BLE1_CUSTOM5 = "ble1Custom5";

    // BLE Custom 2
    public static final String KEY_BLE2_CUSTOM1 = "ble2Custom1";
    public static final String KEY_BLE2_CUSTOM2 = "ble2Custom2";
    public static final String KEY_BLE2_CUSTOM3 = "ble2Custom3";
    public static final String KEY_BLE2_CUSTOM4 = "ble2Custom4";
    public static final String KEY_BLE2_CUSTOM5 = "ble2Custom5";

    // BLE Custom 3
    public static final String KEY_BLE3_CUSTOM1 = "ble3Custom1";
    public static final String KEY_BLE3_CUSTOM2 = "ble3Custom2";
    public static final String KEY_BLE3_CUSTOM3 = "ble3Custom3";
    public static final String KEY_BLE3_CUSTOM4 = "ble3Custom4";
    public static final String KEY_BLE3_CUSTOM5 = "ble3Custom5";
    // BLE 4 Custom
    public static final String KEY_BLE4_CUSTOM1 = "ble4Custom1";
    public static final String KEY_BLE4_CUSTOM2 = "ble4Custom2";
    public static final String KEY_BLE4_CUSTOM3 = "ble4Custom3";
    public static final String KEY_BLE4_CUSTOM4 = "ble4Custom4";
    public static final String KEY_BLE4_CUSTOM5 = "ble4Custom5";

    // CAN parameters
    public static final String KEY_ACCELERATOR = "acceleratorPedal";
    public static final String KEY_TOTAL_MILEAGE = "totalMileage";

    public static final String KEY_DOOR_STATUS = "doorStatus";
    public static final String KEY_PROGRAM_NUMBER = "programNumber";
    public static final String KEY_MODULE_ID_8B = "moduleId8b";
    public static final String KEY_MODULE_ID_17B = "moduleId17b";
    public static final String KEY_ENGINE_WORKTIME = "engineWorktime";
    public static final String KEY_ENGINE_WORKTIME_COUNTED = "engineWorktimeCounted";
    public static final String KEY_TOTAL_MILEAGE_COUNTED = "totalMileageCounted";
    public static final String KEY_FUEL_CONSUMED_COUNTED = "fuelConsumedCounted";
    public static final String KEY_ADBLUE_PERCENT = "adbluePercent";
    public static final String KEY_ADBLUE_LEVEL = "adblueLevel";
    public static final String KEY_AXLE1_LOAD = "axle1Load";
    public static final String KEY_AXLE2_LOAD = "axle2Load";
    public static final String KEY_AXLE3_LOAD = "axle3Load";
    public static final String KEY_AXLE4_LOAD = "axle4Load";
    public static final String KEY_AXLE5_LOAD = "axle5Load";
    public static final String KEY_CONTROL_FLAGS = "controlFlags";
    public static final String KEY_AGR_FLAGS = "agriculturalFlags";
    public static final String KEY_HARVEST_TIME = "harvestTime";
    public static final String KEY_HARVEST_AREA = "harvestArea";
    // CAN / Agricultural Parameters
    public static final String KEY_MOWING_EFFICIENCY = "mowingEfficiency";
    public static final String KEY_GRAIN_VOLUME = "grainVolume";
    public static final String KEY_GRAIN_MOISTURE = "grainMoisture";
    public static final String KEY_HARVEST_DRUM_RPM = "harvestDrumRpm";
    public static final String KEY_HARVEST_DRUM_GAP = "harvestDrumGap";
    public static final String KEY_SECURITY_FLAGS = "securityFlags";
    public static final String KEY_TACHO_TOTAL_DISTANCE = "tachoTotalDistance";
    public static final String KEY_TRIP_DISTANCE = "tripDistance";
    public static final String KEY_TACHO_SPEED = "tachoSpeed";
    public static final String KEY_TACHO_CARD_PRESENT = "tachoCardPresent";
    public static final String KEY_DRIVER1_STATES = "driver1States";
    public static final String KEY_DRIVER2_STATES = "driver2States";
    public static final String KEY_DRIVER1_CONT_DRIVE = "driver1ContDrive";
    public static final String KEY_DRIVER2_CONT_DRIVE = "driver2ContDrive";
    public static final String KEY_DRIVER1_BREAK_TIME = "driver1BreakTime";
    public static final String KEY_DRIVER2_BREAK_TIME = "driver2BreakTime";
    public static final String KEY_DRIVER1_ACTIVITY = "driver1Activity";
    public static final String KEY_DRIVER2_ACTIVITY = "driver2Activity";
    public static final String KEY_DRIVER1_CUM_DRIVE = "driver1CumDrive";
    public static final String KEY_DRIVER2_CUM_DRIVE = "driver2CumDrive";
    public static final String KEY_DRIVER1_ID_HIGH = "driver1IdHigh";
    public static final String KEY_DRIVER1_ID_LOW = "driver1IdLow";
    public static final String KEY_DRIVER2_ID_HIGH = "driver2IdHigh";
    public static final String KEY_DRIVER2_ID_LOW = "driver2IdLow";
    public static final String KEY_BATTERY_TEMP = "batteryTemp";
    public static final String KEY_HV_BATTERY_LEVEL = "hvBatteryLevel";
    public static final String KEY_ARM_SLOPE = "armSlope";
    public static final String KEY_ARM_ROTATION = "armRotation";
    public static final String KEY_ARM_EJECT = "armEject";
    public static final String KEY_ARM_DISTANCE = "armDistance";
    // Agricultural / CAN Extended Parameters
    public static final String KEY_ARM_HEIGHT = "armHeight";
    public static final String KEY_DRILL_RPM = "drillRpm";
    public static final String KEY_SALT_SQ_METER = "saltSqMeter";
    public static final String KEY_BATTERY_VOLTAGE = "batteryVoltage";
    public static final String KEY_FINE_SALT = "fineSalt";
    public static final String KEY_COARSE_SALT = "coarseSalt";
    public static final String KEY_DIMIX = "dimix";
    public static final String KEY_COARSE_CALCIUM = "coarseCalcium";
    public static final String KEY_CALCIUM_CHLORIDE = "calciumChloride";
    public static final String KEY_SODIUM_CHLORIDE = "sodiumChloride";
    public static final String KEY_MAGNESIUM_CHLORIDE = "magnesiumChloride";
    public static final String KEY_GRAVEL = "gravel";
    public static final String KEY_SAND = "sand";
    public static final String KEY_WIDTH_LEFT = "widthLeft";
    public static final String KEY_WIDTH_RIGHT = "widthRight";
    public static final String KEY_SALT_HOURS = "saltHours";
    public static final String KEY_SALT_DISTANCE = "saltDistance";
    public static final String KEY_LOAD_WEIGHT = "loadWeight";
    public static final String KEY_RETARDER_LOAD = "retarderLoad";
    public static final String KEY_CRUISE_TIME = "cruiseTime";
    public static final String KEY_RANGE_BATTERY = "rangeBattery";
    public static final String KEY_RANGE_FUEL = "rangeFuel";
    public static final String KEY_DTC_CODES = "dtcCodes";
    public static final String KEY_SECURITY_FLAGS_P4 = "securityFlagsP4";
    public static final String KEY_CONTROL_FLAGS_P4 = "controlFlagsP4";
    public static final String KEY_INDICATOR_FLAGS_P4 = "indicatorFlagsP4";
    public static final String KEY_AGRICULTURE_FLAGS_P4 = "agricultureFlagsP4";
    public static final String KEY_UTILITY_FLAGS_P4 = "utilityFlagsP4";
    // Cistern / LNG / LPG
    public static final String KEY_CISTERN_STATE_FLAGS_P4 = "cisternStateFlagsP4";
    public static final String KEY_LNG_USED = "lngUsed";
    public static final String KEY_LNG_USED_COUNTED = "lngUsedCounted";
    public static final String KEY_LNG_LEVEL_PROC = "lngLevelProc";
    public static final String KEY_LNG_LEVEL_KG = "lngLevelKg";
    public static final String KEY_LPG_USED = "lpgUsed";
    public static final String KEY_LPG_USED_COUNTED = "lpgUsedCounted";
    public static final String KEY_LPG_LEVEL_PROC = "lpgLevelProc";
    public static final String KEY_LPG_LEVEL_LITERS = "lpgLevelLiters";

    // SSF flags
    public static final String KEY_SSF_IGNITION = "ssfIgnition";
    public static final String KEY_SSF_KEY_IN_IGNITION = "ssfKeyInIgnition";
    public static final String KEY_SSF_WEBASO = "ssfWebaso";
    public static final String KEY_SSF_ENGINE_WORKING = "ssfEngineWorking";
    public static final String KEY_SSF_STANDALONE_ENGINE = "ssfStandaloneEngine";
    public static final String KEY_SSF_READY_TO_DRIVE = "ssfReadyToDrive";
    public static final String KEY_SSF_ENGINE_WORKING_CNG = "ssfEngineWorkingCng";
    public static final String KEY_SSF_WORK_MODE = "ssfWorkMode";
    public static final String KEY_SSF_OPERATOR = "ssfOperator";
    public static final String KEY_SSF_INTERLOCK = "ssfInterlock";
    public static final String KEY_SSF_ENGINE_LOCK_ACTIVE = "ssfEngineLockActive";
    public static final String KEY_SSF_REQUEST_LOCK_ENGINE = "ssfRequestLockEngine";
    public static final String KEY_SSF_HANDBRAKE_ACTIVE = "ssfHandbrakeActive";
    public static final String KEY_SSF_FOOTBRAKE_ACTIVE = "ssfFootbrakeActive";
    public static final String KEY_SSF_CLUTCH_PUSHED = "ssfClutchPushed";
    public static final String KEY_SSF_HAZARD_WARNING = "ssfHazardWarning";
    public static final String KEY_SSF_FRONT_LEFT_DOOR = "ssfFrontLeftDoor";
    public static final String KEY_SSF_FRONT_RIGHT_DOOR = "ssfFrontRightDoor";
    public static final String KEY_SSF_REAR_LEFT_DOOR = "ssfRearLeftDoor";
    public static final String KEY_SSF_REAR_RIGHT_DOOR = "ssfRearRightDoor";
    public static final String KEY_SSF_TRUNK_DOOR = "ssfTrunkDoor";
    public static final String KEY_SSF_ENGINE_COVER = "ssfEngineCover";
    // SSF extended
    public static final String KEY_SSF_ROOF_OPEN = "ssfRoofOpen";
    public static final String KEY_SSF_CHARGING_WIRE = "ssfChargingWire";
    public static final String KEY_SSF_BATTERY_CHARGING = "ssfBatteryCharging";
    public static final String KEY_SSF_ELECTRIC_ENGINE = "ssfElectricEngine";
    public static final String KEY_SSF_CAR_CLOSED_FACTORY = "ssfCarClosedFactory";
    public static final String KEY_SSF_CAR_IS_CLOSED = "ssfCarIsClosed";
    public static final String KEY_SSF_FACTORY_ALARM_ACTUATED = "ssfFactoryAlarmActuated";
    public static final String KEY_SSF_FACTORY_ALARM_EMULATED = "ssfFactoryAlarmEmulated";
    public static final String KEY_SSF_SIGNAL_CLOSE_FACTORY = "ssfSignalCloseFactory";
    public static final String KEY_SSF_SIGNAL_OPEN_FACTORY = "ssfSignalOpenFactory";
    public static final String KEY_SSF_REARMING_SIGNAL = "ssfRearmingSignal";
    public static final String KEY_SSF_TRUNK_REMOTE = "ssfTrunkRemote";
    public static final String KEY_SSF_CAN_SLEEP = "ssfCanSleep";
    public static final String KEY_SSF_FACTORY_REMOTE_3X = "ssfFactoryRemote3x";
    public static final String KEY_SSF_FACTORY_ARMED = "ssfFactoryArmed";
    public static final String KEY_SSF_PARKING_GEAR = "ssfParkingGear";
    public static final String KEY_SSF_REVERSE_GEAR = "ssfReverseGear";
    public static final String KEY_SSF_NEUTRAL_GEAR = "ssfNeutralGear";
    public static final String KEY_SSF_DRIVE_ACTIVE = "ssfDriveActive";
    public static final String KEY_SSF_ENGINE_WORKING_DUALFUEL = "ssfEngineWorkingDualFuel";
    public static final String KEY_SSF_ENGINE_WORKING_LPG = "ssfEngineWorkingLpg";

    // CSF flags
    public static final String KEY_CSF_PARKING_LIGHTS = "csfParkingLights";
    public static final String KEY_CSF_DIPPED_HEADLIGHTS = "csfDippedHeadlights";
    public static final String KEY_CSF_FULL_BEAM_HEADLIGHTS = "csfFullBeamHeadlights";
    public static final String KEY_CSF_REAR_FOG_LIGHTS = "csfRearFogLights";
    public static final String KEY_CSF_FRONT_FOG_LIGHTS = "csfFrontFogLights";
    public static final String KEY_CSF_ADDITIONAL_FRONT_LIGHTS = "csfAdditionalFrontLights";
    public static final String KEY_CSF_ADDITIONAL_REAR_LIGHTS = "csfAdditionalRearLights";
    public static final String KEY_CSF_LIGHT_SIGNAL = "csfLightSignal";
    public static final String KEY_CSF_AIR_CONDITIONING = "csfAirConditioning";
    public static final String KEY_CSF_CRUISE_CONTROL = "csfCruiseControl";
    public static final String KEY_CSF_AUTOMATIC_RETARDER = "csfAutomaticRetarder";
    public static final String KEY_CSF_MANUAL_RETARDER = "csfManualRetarder";
    public static final String KEY_CSF_DRIVER_SEATBELT = "csfDriverSeatbelt";
    public static final String KEY_CSF_FRONT_DRIVER_SEATBELT = "csfFrontDriverSeatbelt";
    public static final String KEY_CSF_LEFT_DRIVER_SEATBELT = "csfLeftDriverSeatbelt";
    public static final String KEY_CSF_RIGHT_DRIVER_SEATBELT = "csfRightDriverSeatbelt";
    public static final String KEY_CSF_CENTRE_DRIVER_SEATBELT = "csfCentreDriverSeatbelt";
    public static final String KEY_CSF_FRONT_PASSENGER_PRESENT = "csfFrontPassengerPresent";
    public static final String KEY_CSF_PTO = "csfPto";
    public static final String KEY_CSF_FRONT_DIFF_LOCKED = "csfFrontDiffLocked";
    public static final String KEY_CSF_REAR_DIFF_LOCKED = "csfRearDiffLocked";
    public static final String KEY_CSF_CENTRAL_DIFF_4HI_LOCKED = "csfCentralDiff4HiLocked";
    public static final String KEY_CSF_REAR_DIFF_4LO_LOCKED = "csfRearDiff4LoLocked";
    public static final String KEY_CSF_TRAILER_AXLE1_LIFT = "csfTrailerAxle1Lift";
    public static final String KEY_CSF_TRAILER_AXLE2_LIFT = "csfTrailerAxle2Lift";
    public static final String KEY_CSF_TRAILER_CONNECTED = "csfTrailerConnected";
    public static final String KEY_CSF_START_STOP_INACTIVE = "csfStartStopInactive";
    public static final String KEY_ISF_CHECK_ENGINE = "isfCheckEngine";
    public static final String KEY_ISF_ABS = "isfAbs";
    public static final String KEY_ISF_ESP = "isfEsp";
    public static final String KEY_ISF_ESP_TURNED_OFF = "isfEspTurnedOff";
    public static final String KEY_ISF_STOP = "isfStop";
    public static final String KEY_ISF_OIL_LEVEL = "isfOilLevel";
    public static final String KEY_ISF_COOLANT_LEVEL = "isfCoolantLevel";
    public static final String KEY_ISF_BATTERY_NOT_CHARGING = "isfBatteryNotCharging";
    public static final String KEY_ISF_HANDBRAKE = "isfHandbrake";
    public static final String KEY_ISF_AIRBAG = "isfAirbag";
    public static final String KEY_ISF_EPS = "isfEps";
    public static final String KEY_ISF_WARNING = "isfWarning";
    public static final String KEY_ISF_LIGHTS_FAILURE = "isfLightsFailure";
    public static final String KEY_ISF_LOW_TIRE_PRESSURE = "isfLowTirePressure";
    public static final String KEY_ISF_BRAKE_PADS_WEAR = "isfBrakePadsWear";
    public static final String KEY_ISF_LOW_FUEL = "isfLowFuel";
    public static final String KEY_ISF_MAINTENANCE_REQUIRED = "isfMaintenanceRequired";
    public static final String KEY_ISF_GLOW_PLUG = "isfGlowPlug";
    public static final String KEY_ISF_FAP = "isfFap";
    public static final String KEY_ISF_EPC = "isfEpc";
    public static final String KEY_ISF_OIL_FILTER_CLOGGED = "isfOilFilterClogged";
    public static final String KEY_ISF_LOW_ENGINE_OIL_PRESSURE = "isfLowEngineOilPressure";
    public static final String KEY_ISF_ENGINE_OIL_HIGH_TEMP = "isfEngineOilHighTemp";
    public static final String KEY_ISF_LOW_COOLANT = "isfLowCoolant";
    public static final String KEY_ISF_HYDRAULIC_OIL_FILTER_CLOGGED = "isfHydraulicOilFilterClogged";
    public static final String KEY_ISF_HYDRAULIC_LOW_PRESSURE = "isfHydraulicLowPressure";
    public static final String KEY_ISF_HYDRAULIC_OIL_LOW = "isfHydraulicOilLow";
    public static final String KEY_ISF_HYDRAULIC_HIGH_TEMP = "isfHydraulicHighTemp";
    public static final String KEY_ISF_HYDRAULIC_OVERFLOW = "isfHydraulicOverflow";
    public static final String KEY_ISF_AIR_FILTER_CLOGGED = "isfAirFilterClogged";
    public static final String KEY_ISF_FUEL_FILTER_CLOGGED = "isfFuelFilterClogged";
    public static final String KEY_ISF_WATER_IN_FUEL = "isfWaterInFuel";
    public static final String KEY_ISF_BRAKE_SYSTEM_FILTER_CLOGGED = "isfBrakeSystemFilterClogged";
    public static final String KEY_ISF_LOW_WASHER_FLUID = "isfLowWasherFluid";
    public static final String KEY_ISF_LOW_ADBLUE = "isfLowAdblue";
    public static final String KEY_ISF_LOW_TRAILER_TIRE_PRESSURE = "isfLowTrailerTirePressure";
    public static final String KEY_ISF_TRAILER_BRAKE_LINING_WEAR = "isfTrailerBrakeLiningWear";
    public static final String KEY_ISF_TRAILER_BRAKE_HIGH_TEMP = "isfTrailerBrakeHighTemp";
    public static final String KEY_ISF_TRAILER_PNEUMATIC_SUPPLY = "isfTrailerPneumaticSupply";
    public static final String KEY_ISF_LOW_CNG = "isfLowCng";

    public static final String KEY_ASF_RIGHT_JOYSTICK_RIGHT = "asfRightJoystickRight";
    public static final String KEY_ASF_RIGHT_JOYSTICK_LEFT = "asfRightJoystickLeft";
    public static final String KEY_ASF_RIGHT_JOYSTICK_FORWARD = "asfRightJoystickForward";
    public static final String KEY_ASF_RIGHT_JOYSTICK_BACK = "asfRightJoystickBack";
    public static final String KEY_ASF_LEFT_JOYSTICK_RIGHT = "asfLeftJoystickRight";
    public static final String KEY_ASF_LEFT_JOYSTICK_LEFT = "asfLeftJoystickLeft";
    public static final String KEY_ASF_LEFT_JOYSTICK_FORWARD = "asfLeftJoystickForward";
    public static final String KEY_ASF_LEFT_JOYSTICK_BACK = "asfLeftJoystickBack";

    public static final String KEY_ASF_REAR_HYDRAULIC1 = "asfRearHydraulic1";
    public static final String KEY_ASF_REAR_HYDRAULIC2 = "asfRearHydraulic2";
    public static final String KEY_ASF_REAR_HYDRAULIC3 = "asfRearHydraulic3";
    public static final String KEY_ASF_REAR_HYDRAULIC4 = "asfRearHydraulic4";
    public static final String KEY_ASF_FRONT_HYDRAULIC1 = "asfFrontHydraulic1";
    public static final String KEY_ASF_FRONT_HYDRAULIC2 = "asfFrontHydraulic2";
    public static final String KEY_ASF_FRONT_HYDRAULIC3 = "asfFrontHydraulic3";
    public static final String KEY_ASF_FRONT_HYDRAULIC4 = "asfFrontHydraulic4";

    public static final String KEY_ASF_FRONT_HITCH = "asfFrontHitch";
    public static final String KEY_ASF_REAR_HITCH = "asfRearHitch";
    public static final String KEY_ASF_FRONT_PTO = "asfFrontPto";
    public static final String KEY_ASF_REAR_PTO = "asfRearPto";
    public static final String KEY_ASF_MOWING = "asfMowing";
    public static final String KEY_ASF_THRESHING = "asfThreshing";
    public static final String KEY_ASF_GRAIN_RELEASE = "asfGrainRelease";
    public static final String KEY_ASF_GRAIN_TANK_FULL_100 = "asfGrainTankFull100";
    public static final String KEY_ASF_GRAIN_TANK_FULL_70 = "asfGrainTankFull70";
    public static final String KEY_ASF_GRAIN_TANK_OPENED = "asfGrainTankOpened";
    public static final String KEY_ASF_UNLOADER_DRIVE = "asfUnloaderDrive";
    public static final String KEY_ASF_CLEANING_FAN_CTRL_OFF = "asfCleaningFanCtrlOff";
    public static final String KEY_ASF_THRESHING_DRUM_CTRL_OFF = "asfThreshingDrumCtrlOff";
    public static final String KEY_ASF_STRAW_WALKER_CLOGGED = "asfStrawWalkerClogged";
    public static final String KEY_ASF_THRESHING_DRUM_CLEARANCE = "asfThreshingDrumClearance";
    public static final String KEY_ASF_HYDRAULICS_TEMP_LOW = "asfHydraulicsTempLow";
    public static final String KEY_ASF_HYDRAULICS_TEMP_HIGH = "asfHydraulicsTempHigh";
    public static final String KEY_ASF_EAR_AUGER_SPEED_LOW = "asfEarAugerSpeedLow";
    public static final String KEY_ASF_GRAIN_AUGER_SPEED_LOW = "asfGrainAugerSpeedLow";
    public static final String KEY_ASF_STRAW_CHOPPER_SPEED_LOW = "asfStrawChopperSpeedLow";
    public static final String KEY_ASF_STRAW_SHAKER_SPEED_LOW = "asfStrawShakerSpeedLow";
    public static final String KEY_ASF_FEEDER_SPEED_LOW = "asfFeederSpeedLow";
    public static final String KEY_ASF_STRAW_CHOPPER_ON = "asfStrawChopperOn";
    public static final String KEY_ASF_CORN_HEADER_CONNECTED = "asfCornHeaderConnected";
    public static final String KEY_ASF_GRAIN_HEADER_CONNECTED = "asfGrainHeaderConnected";
    public static final String KEY_ASF_FEEDER_REVERSE_ON = "asfFeederReverseOn";
    public static final String KEY_ASF_HYDRAULIC_PUMP_FILTER_CLOGGED = "asfHydraulicPumpFilterClogged";
    public static final String KEY_ASF_ADAPTER_PRESSURE_FILTER = "asfAdapterPressureFilter";
    public static final String KEY_ASF_SERVICE2_REQUIRED = "asfService2Required";
    public static final String KEY_ASF_DRAIN_FILTER_CLOGGED = "asfDrainFilterClogged";

    // ASF Section Spraying
    public static final String KEY_ASF_SECTION1_SPRAYING = "asfSection1Spraying";
    public static final String KEY_ASF_SECTION2_SPRAYING = "asfSection2Spraying";
    public static final String KEY_ASF_SECTION3_SPRAYING = "asfSection3Spraying";
    public static final String KEY_ASF_SECTION4_SPRAYING = "asfSection4Spraying";
    public static final String KEY_ASF_SECTION5_SPRAYING = "asfSection5Spraying";
    public static final String KEY_ASF_SECTION6_SPRAYING = "asfSection6Spraying";
    public static final String KEY_ASF_SECTION7_SPRAYING = "asfSection7Spraying";
    public static final String KEY_ASF_SECTION8_SPRAYING = "asfSection8Spraying";
    public static final String KEY_ASF_SECTION9_SPRAYING = "asfSection9Spraying";

    // USF Parameters
    public static final String KEY_USF_SPREADING = "usfSpreading";
    public static final String KEY_USF_POURING_CHEMICALS = "usfPouringChemicals";
    public static final String KEY_USF_CONVEYOR_BELT = "usfConveyorBelt";
    public static final String KEY_USF_SALT_SPREADER_DRIVE_WHEEL = "usfSaltSpreaderDriveWheel";
    public static final String KEY_USF_BRUSHES = "usfBrushes";
    public static final String KEY_USF_VACUUM_CLEANER = "usfVacuumCleaner";
    public static final String KEY_USF_WATER_SUPPLY = "usfWaterSupply";
    public static final String KEY_USF_SPREADING_ALT = "usfSpreadingAlt";
    public static final String KEY_USF_LIQUID_PUMP = "usfLiquidPump";
    public static final String KEY_USF_UNLOADING_HOPPER = "usfUnloadingFromHopper";
    public static final String KEY_USF_LOW_SALT_LEVEL = "usfLowSaltLevel";
    public static final String KEY_USF_LOW_WATER_LEVEL = "usfLowWaterLevel";
    public static final String KEY_USF_CHEMICALS = "usfChemicals";
    public static final String KEY_USF_COMPRESSOR = "usfCompressor";
    // USF Extra
    public static final String KEY_USF_WATER_VALVE_OPENED = "usfWaterValveOpened";
    public static final String KEY_USF_CABIN_MOVED_UP = "usfCabinMovedUp";
    public static final String KEY_USF_CABIN_MOVED_DOWN = "usfCabinMovedDown";
    public static final String KEY_USF_HYDRAULICS_NOT_PERMITTED = "usfHydraulicsNotPermitted";

    // CiSF Sections
    public static final String KEY_CISF_SECTION1_PRESENCE = "cisfSection1Presence";
    public static final String KEY_CISF_SECTION1_FILLED = "cisfSection1Filled";
    public static final String KEY_CISF_SECTION1_OVERFILLED = "cisfSection1Overfilled";
    public static final String KEY_CISF_SECTION2_PRESENCE = "cisfSection2Presence";
    public static final String KEY_CISF_SECTION2_FILLED = "cisfSection2Filled";
    public static final String KEY_CISF_SECTION2_OVERFILLED = "cisfSection2Overfilled";
    public static final String KEY_CISF_SECTION3_PRESENCE = "cisfSection3Presence";
    public static final String KEY_CISF_SECTION3_FILLED = "cisfSection3Filled";
    public static final String KEY_CISF_SECTION3_OVERFILLED = "cisfSection3Overfilled";
    public static final String KEY_CISF_SECTION4_PRESENCE = "cisfSection4Presence";
    public static final String KEY_CISF_SECTION4_FILLED = "cisfSection4Filled";
    public static final String KEY_CISF_SECTION4_OVERFILLED = "cisfSection4Overfilled";
    public static final String KEY_CISF_SECTION5_PRESENCE = "cisfSection5Presence";
    public static final String KEY_CISF_SECTION5_FILLED = "cisfSection5Filled";
    public static final String KEY_CISF_SECTION5_OVERFILLED = "cisfSection5Overfilled";
    public static final String KEY_CISF_SECTION6_PRESENCE = "cisfSection6Presence";
    public static final String KEY_CISF_SECTION6_FILLED = "cisfSection6Filled";
    public static final String KEY_CISF_SECTION6_OVERFILLED = "cisfSection6Overfilled";
    public static final String KEY_CISF_SECTION7_PRESENCE = "cisfSection7Presence";
    public static final String KEY_CISF_SECTION7_FILLED = "cisfSection7Filled";
    public static final String KEY_CISF_SECTION7_OVERFILLED = "cisfSection7Overfilled";
    public static final String KEY_CISF_SECTION8_PRESENCE = "cisfSection8Presence";
    public static final String KEY_CISF_SECTION8_FILLED = "cisfSection8Filled";
    public static final String KEY_CISF_SECTION8_OVERFILLED = "cisfSection8Overfilled";

    // Service & Vehicle
    public static final String KEY_DISTANCE_TO_NEXT_SERVICE = "distanceToNextService";
    public static final String KEY_CNG_LEVEL_KG = "cngLevelKg";
    public static final String KEY_DISTANCE_FROM_NEED_SERVICE = "distanceFromNeedService";
    public static final String KEY_DISTANCE_FROM_LAST_SERVICE = "distanceFromLastService";
    public static final String KEY_TIME_TO_NEXT_SERVICE = "timeToNextService";
    public static final String KEY_TIME_FROM_NEED_SERVICE = "timeFromNeedService";
    public static final String KEY_TIME_FROM_LAST_SERVICE = "timeFromLastService";
    public static final String KEY_DISTANCE_TO_NEXT_OIL_SERVICE = "distanceToNextOilService";
    public static final String KEY_TIME_TO_NEXT_OIL_SERVICE = "timeToNextOilService";
    public static final String KEY_LVCAN_VEHICLE_RANGE = "lvcanVehicleRange";
    public static final String KEY_LVCAN_TOTAL_CNG_COUNTED = "lvcanTotalCngCounted";
    public static final String KEY_SHORT_FUEL_TRIM = "shortFuelTrim";

    // Bale Count
    public static final String KEY_TOTAL_BALE_COUNT = "totalBaleCount";
    public static final String KEY_BALE_COUNT = "baleCount";
    public static final String KEY_CUT_BALE_COUNT = "cutBaleCount";
    public static final String KEY_BALE_SLICES = "baleSlices";
    public static final String KEY_FAULT_CODES = "faultCodes";

    // Road Speed
    public static final String KEY_LVCAN_MAX_ROAD_SPEED = "lvcanMaxRoadSpeed";
    public static final String KEY_LVCAN_EXCEEDED_ROAD_SPEED = "lvcanExceededRoadSpeed";

    // Road Sign Features
    public static final String KEY_RSF_SPEED_LIMIT_SIGN = "rsfSpeedLimitSign";
    public static final String KEY_RSF_END_SPEED_LIMIT_SIGN = "rsfEndSpeedLimitSign";
    public static final String KEY_RSF_SPEED_EXCEEDED = "rsfSpeedExceeded";
    public static final String KEY_RSF_TIME_SPEED_LIMIT_SIGN = "rsfTimeSpeedLimitSign";
    public static final String KEY_RSF_WEATHER_SPEED_LIMIT_SIGN = "rsfWeatherSpeedLimitSign";

    public Position() {
    }

    public Position(String protocol) {
        this.protocol = protocol;
    }

    private String protocol;

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    private Date serverTime = new Date();

    public Date getServerTime() {
        return serverTime;
    }

    public void setServerTime(Date serverTime) {
        this.serverTime = serverTime;
    }

    private Date deviceTime;

    public Date getDeviceTime() {
        return deviceTime;
    }

    public void setDeviceTime(Date deviceTime) {
        this.deviceTime = deviceTime;
    }

    private Date fixTime;

    public Date getFixTime() {
        return fixTime;
    }

    public void setFixTime(Date fixTime) {
        this.fixTime = fixTime;
    }

    @QueryIgnore
    public void setTime(Date time) {
        setDeviceTime(time);
        setFixTime(time);
    }

    private boolean outdated;

    @JsonIgnore
    @QueryIgnore
    public boolean getOutdated() {
        return outdated;
    }

    @JsonIgnore
    @QueryIgnore
    public void setOutdated(boolean outdated) {
        this.outdated = outdated;
    }

    private boolean valid;

    public boolean getValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    private double latitude;

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude out of range");
        }
        this.latitude = latitude;
    }

    private double longitude;

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude out of range");
        }
        this.longitude = longitude;
    }

    private double altitude; // value in meters

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    private double speed; // value in knots

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    private double course;

    public double getCourse() {
        return course;
    }

    public void setCourse(double course) {
        this.course = course;
    }

    private String address;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    private double accuracy;

    public double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    private Network network;

    public Network getNetwork() {
        return network;
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    private List<Long> geofenceIds;

    public List<Long> getGeofenceIds() {
        return geofenceIds;
    }

    public void setGeofenceIds(List<? extends Number> geofenceIds) {
        if (geofenceIds != null) {
            this.geofenceIds = geofenceIds.stream().map(Number::longValue).toList();
        } else {
            this.geofenceIds = null;
        }
    }

    public void addAlarm(String alarm) {
        if (alarm != null) {
            if (hasAttribute(KEY_ALARM)) {
                set(KEY_ALARM, getAttributes().get(KEY_ALARM) + "," + alarm);
            } else {
                set(KEY_ALARM, alarm);
            }
        }
    }

    @JsonIgnore
    @QueryIgnore
    @Override
    public String getType() {
        return super.getType();
    }

    @JsonIgnore
    @QueryIgnore
    @Override
    public void setType(String type) {
        super.setType(type);
    }

}
