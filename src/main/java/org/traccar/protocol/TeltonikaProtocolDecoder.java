/*
 * Copyright 2013 - 2023 Anton Tananaev (anton@traccar.org)
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
package org.traccar.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.helper.BufferUtil;
import org.traccar.session.DeviceSession;
import org.traccar.NetworkMessage;
import org.traccar.Protocol;
import org.traccar.config.Keys;
import org.traccar.helper.BitUtil;
import org.traccar.helper.Checksum;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.CellTower;
import org.traccar.model.Network;
import org.traccar.model.Position;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class TeltonikaProtocolDecoder extends BaseProtocolDecoder {

    private static final int IMAGE_PACKET_MAX = 2048;

    private static final Map<Integer, Map<Set<String>, BiConsumer<Position, ByteBuf>>> PARAMETERS = new HashMap<>();

    private final boolean connectionless;
    private boolean extended;
    private final Map<Long, ByteBuf> photos = new HashMap<>();

    public void setExtended(boolean extended) {
        this.extended = extended;
    }

    public TeltonikaProtocolDecoder(Protocol protocol, boolean connectionless) {
        super(protocol);
        this.connectionless = connectionless;
    }

    @Override
    protected void init() {
        this.extended = getConfig().getBoolean(Keys.PROTOCOL_EXTENDED.withPrefix(getProtocolName()));
    }

    private void parseIdentification(Channel channel, SocketAddress remoteAddress, ByteBuf buf) {

        int length = buf.readUnsignedShort();
        String imei = buf.toString(buf.readerIndex(), length, StandardCharsets.US_ASCII);
        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, imei);

        if (channel != null) {
            ByteBuf response = Unpooled.buffer(1);
            if (deviceSession != null) {
                response.writeByte(1);
            } else {
                response.writeByte(0);
            }
            channel.writeAndFlush(new NetworkMessage(response, remoteAddress));
        }
    }

    public static final int CODEC_GH3000 = 0x07;
    public static final int CODEC_8 = 0x08;
    public static final int CODEC_8_EXT = 0x8E;
    public static final int CODEC_12 = 0x0C;
    public static final int CODEC_13 = 0x0D;
    public static final int CODEC_16 = 0x10;

    private void sendImageRequest(Channel channel, SocketAddress remoteAddress, long id, int offset, int size) {
        if (channel != null) {
            ByteBuf response = Unpooled.buffer();
            response.writeInt(0);
            response.writeShort(0);
            response.writeShort(19); // length
            response.writeByte(CODEC_12);
            response.writeByte(1); // nod
            response.writeByte(0x0D); // camera
            response.writeInt(11); // payload length
            response.writeByte(2); // command
            response.writeInt((int) id);
            response.writeInt(offset);
            response.writeShort(size);
            response.writeByte(1); // nod
            response.writeShort(0);
            response.writeShort(Checksum.crc16(
                    Checksum.CRC16_IBM, response.nioBuffer(8, response.readableBytes() - 10)));
            channel.writeAndFlush(new NetworkMessage(response, remoteAddress));
        }
    }

    private void decodeSerial(
            Channel channel, SocketAddress remoteAddress, DeviceSession deviceSession, Position position, ByteBuf buf) {

        getLastLocation(position, null);

        int type = buf.readUnsignedByte();
        if (type == 0x0D) {

            buf.readInt(); // length
            int subtype = buf.readUnsignedByte();
            if (subtype == 0x01) {

                long photoId = buf.readUnsignedInt();
                ByteBuf photo = Unpooled.buffer(buf.readInt());
                photos.put(photoId, photo);
                sendImageRequest(
                        channel, remoteAddress, photoId,
                        0, Math.min(IMAGE_PACKET_MAX, photo.capacity()));

            } else if (subtype == 0x02) {

                long photoId = buf.readUnsignedInt();
                buf.readInt(); // offset
                ByteBuf photo = photos.get(photoId);
                photo.writeBytes(buf, buf.readUnsignedShort());
                if (photo.writableBytes() > 0) {
                    sendImageRequest(
                            channel, remoteAddress, photoId,
                            photo.writerIndex(), Math.min(IMAGE_PACKET_MAX, photo.writableBytes()));
                } else {
                    photos.remove(photoId);
                    try {
                        position.set(Position.KEY_IMAGE, writeMediaFile(deviceSession.getUniqueId(), photo, "jpg"));
                    } finally {
                        photo.release();
                    }
                }

            }

        } else {

            position.set(Position.KEY_TYPE, type);

            int length = buf.readInt();
            if (BufferUtil.isPrintable(buf, length)) {
                String data = buf.readSlice(length).toString(StandardCharsets.US_ASCII).trim();
                if (data.startsWith("UUUUww") && data.endsWith("SSS")) {
                    String[] values = data.substring(6, data.length() - 4).split(";");
                    for (int i = 0; i < 8; i++) {
                        position.set("axle" + (i + 1), Double.parseDouble(values[i]));
                    }
                    position.set("loadTruck", Double.parseDouble(values[8]));
                    position.set("loadTrailer", Double.parseDouble(values[9]));
                    position.set("totalTruck", Double.parseDouble(values[10]));
                    position.set("totalTrailer", Double.parseDouble(values[11]));
                } else {
                    position.set(Position.KEY_RESULT, data);
                }
            } else {
                position.set(Position.KEY_RESULT, ByteBufUtil.hexDump(buf.readSlice(length)));
            }
        }
    }

    private long readValue(ByteBuf buf, int length) {
        return switch (length) {
            case 1 -> buf.readUnsignedByte();
            case 2 -> buf.readUnsignedShort();
            case 4 -> buf.readUnsignedInt();
            default -> buf.readLong();
        };
    }

    private static void register(int id, Set<String> models, BiConsumer<Position, ByteBuf> handler) {
        PARAMETERS.computeIfAbsent(id, key -> new HashMap<>()).put(models, handler);
    }

    static {
        var fmbXXX = Set.of(
                "FMB001", "FMC001", "FMB010", "FMB002", "FMB020", "FMB003", "FMB110", "FMB120", "FMB122", "FMB125",
                "FMB130", "FMB140", "FMU125", "FMB900", "FMB920", "FMB962", "FMB964", "FM3001", "FMB202", "FMB204",
                "FMB206", "FMT100", "MTB100", "FMP100", "MSP500", "FMC125", "FMM125", "FMU130", "FMC130", "FMM130",
                "FMB150", "FMC150", "FMM150", "FMC920");


        register(1, fmbXXX, (p, b) -> p.set(Position.KEY_INPUT1, b.readUnsignedByte()));
        register(2, fmbXXX, (p, b) -> p.set(Position.KEY_INPUT2, b.readUnsignedByte()));
        register(3, fmbXXX, (p, b) -> p.set(Position.KEY_INPUT3, b.readUnsignedByte()));
        register(9, fmbXXX, (p, b) -> p.set(Position.KEY_ANALOG1, b.readUnsignedShort() * 0.001));
        register(10, fmbXXX, (p, b) -> p.set(Position.KEY_SD_STATUS, b.readUnsignedByte()));
        register(11, fmbXXX, (p, b) -> p.set(Position.KEY_ICCID1, ByteBufUtil.hexDump(b.readSlice(8))));
        register(12, fmbXXX, (p, b) -> p.set(Position.KEY_FUEL_USED_GPS, b.readUnsignedInt() * 0.001));
        register(13, fmbXXX, (p, b) -> p.set(Position.KEY_FUEL_RATE_GPS, b.readUnsignedShort()));
        register(4, fmbXXX, (p, b) -> p.set(Position.KEY_PULSE_COUNT1, b.readUnsignedInt()));
        register(16, null, (p, b) -> p.set(Position.KEY_ODOMETER, b.readUnsignedInt()));
        register(17, null, (p, b) -> p.set("axisX", b.readShort()));
        register(18, null, (p, b) -> p.set("axisY", b.readShort()));
        register(19, null, (p, b) -> p.set("axisZ", b.readShort()));
        register(21, fmbXXX, (p, b) -> p.set(Position.KEY_GSM_SIGNAL, b.readUnsignedByte()));
        register(24, fmbXXX, (p, b) -> p.setSpeed(UnitsConverter.knotsFromKph(b.readUnsignedShort())));
        register(25, null, (p, b) -> p.set("bleTemp1", b.readShort() * 0.01));
        register(26, null, (p, b) -> p.set("bleTemp2", b.readShort() * 0.01));
        register(27, null, (p, b) -> p.set("bleTemp3", b.readShort() * 0.01));
        register(28, null, (p, b) -> p.set("bleTemp4", b.readShort() * 0.01));
        register(30, fmbXXX, (p, b) -> p.set(Position.KEY_NUM_DTC, b.readCharSequence(1, StandardCharsets.US_ASCII).toString()));
        register(31, fmbXXX, (p, b) -> p.set(Position.KEY_ENGINE_LOAD, b.readUnsignedByte()));
        register(32, fmbXXX, (p, b) -> p.set(Position.KEY_COOLANT_TEMP, b.readByte()));
        register(33, fmbXXX, (p, b) -> p.set(Position.KEY_SHORT_FUEL_TRIM, b.readByte()));
        register(36, fmbXXX, (p, b) -> p.set(Position.KEY_RPM, b.readUnsignedShort()));
        register(43, fmbXXX, (p, b) -> p.set("milDistance", b.readUnsignedShort()));
        register(57, fmbXXX, (p, b) -> p.set("hybridBatteryLevel", b.readByte()));
        register(66, null, (p, b) -> p.set(Position.KEY_EXTERNAL_VOLTAGE, b.readUnsignedShort() * 0.001));
        register(67, null, (p, b) -> p.set(Position.KEY_BATTERY, b.readUnsignedShort() * 0.001));
        register(68, fmbXXX, (p, b) -> p.set(Position.KEY_BATTERY_CURRENT, b.readUnsignedShort() * 0.001));
        register(72, fmbXXX, (p, b) -> p.set(Position.KEY_DALLAS_TEMP1, b.readInt() * 0.1));
        register(73, fmbXXX, (p, b) -> p.set(Position.KEY_DALLAS_TEMP2, b.readInt() * 0.1));
        register(74, fmbXXX, (p, b) -> p.set(Position.KEY_DALLAS_TEMP3, b.readInt() * 0.1));
        register(75, fmbXXX, (p, b) -> p.set(Position.KEY_DALLAS_TEMP4, b.readInt() * 0.1));
        register(78, fmbXXX, (p, b) -> p.set(Position.KEY_IBUTTON, ByteBufUtil.hexDump(b.readSlice(8))));
        register(80, fmbXXX, (p, b) -> p.set(Position.KEY_DATA_MODE, b.readUnsignedByte()));
        register(81, fmbXXX, (p, b) -> p.set(Position.KEY_OBD_SPEED, b.readUnsignedByte()));
        register(82, fmbXXX, (p, b) -> p.set(Position.KEY_ACCELERATOR, b.readUnsignedByte()));
        register(83, fmbXXX, (p, b) -> p.set(Position.KEY_FUEL_USED, b.readUnsignedInt() * 0.1));
        register(84, fmbXXX, (p, b) -> p.set(Position.KEY_FUEL, b.readUnsignedShort() * 0.1));
        register(85, fmbXXX, (p, b) -> p.set(Position.KEY_RPM, b.readUnsignedShort()));
        register(87, fmbXXX, (p, b) -> p.set(Position.KEY_TOTAL_MILEAGE, b.readUnsignedInt()));
        register(89, fmbXXX, (p, b) -> p.set(Position.KEY_FUEL_LEVEL, b.readUnsignedByte()));
        register(107, fmbXXX, (p, b) -> p.set(Position.KEY_FUEL_USED, b.readUnsignedInt() * 0.1));
        register(110, fmbXXX, (p, b) -> p.set(Position.KEY_FUEL_CONSUMPTION, b.readUnsignedShort() * 0.1));
        register(113, fmbXXX, (p, b) -> p.set(Position.KEY_BATTERY_LEVEL, b.readUnsignedByte()));
        register(115, fmbXXX, (p, b) -> p.set(Position.KEY_ENGINE_TEMP, b.readShort() * 0.1));
        register(701, Set.of("FMC640", "FMC650", "FMM640"), (p, b) -> p.set("bleTemp1", b.readShort() * 0.01));
        register(702, Set.of("FMC640", "FMC650", "FMM640"), (p, b) -> p.set("bleTemp2", b.readShort() * 0.01));
        register(703, Set.of("FMC640", "FMC650", "FMM640"), (p, b) -> p.set("bleTemp3", b.readShort() * 0.01));
        register(704, Set.of("FMC640", "FMC650", "FMM640"), (p, b) -> p.set("bleTemp4", b.readShort() * 0.01));
        register(179, fmbXXX, (p, b) -> p.set(Position.KEY_OUTPUT1, b.readUnsignedByte()));
        register(180, fmbXXX, (p, b) -> p.set(Position.KEY_OUTPUT2, b.readUnsignedByte()));
        register(181, null, (p, b) -> p.set(Position.KEY_PDOP, b.readUnsignedShort() * 0.1));
        register(182, null, (p, b) -> p.set(Position.KEY_HDOP, b.readUnsignedShort() * 0.1));
        register(199, null, (p, b) -> p.set(Position.KEY_ODOMETER_TRIP, b.readUnsignedInt()));
        register(200, fmbXXX, (p, b) -> p.set(Position.KEY_SLEEP_MODE, b.readUnsignedByte()));
        register(205, fmbXXX, (p, b) -> p.set(Position.KEY_GSM_CELL_ID, b.readUnsignedShort()));
        register(206, fmbXXX, (p, b) -> p.set(Position.KEY_GSM_AREA_CODE, b.readUnsignedShort()));
        register(232, fmbXXX, (p, b) -> p.set("cngStatus", b.readUnsignedByte() > 0));
        register(233, fmbXXX, (p, b) -> p.set("cngUsed", b.readUnsignedInt() * 0.1));
        register(234, fmbXXX, (p, b) -> p.set("cngLevel", b.readUnsignedShort()));
        register(235, fmbXXX, (p, b) -> p.set("oilLevel", b.readUnsignedByte()));
        register(236, null, (p, b) -> {
            p.addAlarm(b.readUnsignedByte() > 0 ? Position.ALARM_GENERAL : null);
        });
        register(239, null, (p, b) -> p.set(Position.KEY_IGNITION, b.readUnsignedByte() > 0));
        register(240, null, (p, b) -> p.set(Position.KEY_MOTION, b.readUnsignedByte() > 0));
        register(241, null, (p, b) -> p.set(Position.KEY_OPERATOR, b.readUnsignedInt()));
        register(246, fmbXXX, (p, b) -> {
            p.addAlarm(b.readUnsignedByte() > 0 ? Position.ALARM_TOW : null);
        });
        register(247, fmbXXX, (p, b) -> {
            p.addAlarm(b.readUnsignedByte() > 0 ? Position.KEY_CRASH_DETECTION : null);
        });
        register(249, fmbXXX, (p, b) -> {
            p.addAlarm(b.readUnsignedByte() > 0 ? Position.ALARM_JAMMING : null);
        });
        register(251, fmbXXX, (p, b) -> {
            p.addAlarm(b.readUnsignedByte() > 0 ? Position.ALARM_IDLE : null);
        });
        register(252, fmbXXX, (p, b) -> {
            p.addAlarm(b.readUnsignedByte() > 0 ? Position.ALARM_POWER_CUT : null);
        });
        register(253, fmbXXX, (p, b) -> p.set(Position.KEY_GREEN_TYPE, b.readUnsignedByte()));
        register(175, fmbXXX, (p, b) -> p.set(Position.KEY_AUTO_GEOFENCE, b.readUnsignedByte()));
        register(636, fmbXXX, (p, b) -> p.set(Position.KEY_CELL_ID, b.readUnsignedInt()));
        register(662, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_CAR_IS_CLOSED, b.readUnsignedByte() == 1));
        register(10644, fmbXXX, (p, b) -> p.set("tempProbe1", b.readShort() / 100.0));
        register(10645, fmbXXX, (p, b) -> p.set("tempProbe2", b.readShort() / 100.0));
        register(10646, fmbXXX, (p, b) -> p.set("tempProbe3", b.readShort() / 100.0));
        register(10647, fmbXXX, (p, b) -> p.set("tempProbe4", b.readShort() / 100.0));
        register(10648, fmbXXX, (p, b) -> p.set("tempProbe5", b.readShort() / 100.0));
        register(10649, fmbXXX, (p, b) -> p.set("tempProbe6", b.readShort() / 100.0));
        register(10800, fmbXXX, (p, b) -> p.set("eyeTemp1", b.readShort() / 100.0));
        register(10801, fmbXXX, (p, b) -> p.set("eyeTemp2", b.readShort() / 100.0));
        register(10802, fmbXXX, (p, b) -> p.set("eyeTemp3", b.readShort() / 100.0));
        register(10803, fmbXXX, (p, b) -> p.set("eyeTemp4", b.readShort() / 100.0));
        register(10832, fmbXXX, (p, b) -> p.set("eyeRoll1", b.readShort()));
        register(10833, fmbXXX, (p, b) -> p.set("eyeRoll2", b.readShort()));
        register(10834, fmbXXX, (p, b) -> p.set("eyeRoll3", b.readShort()));
        register(10835, fmbXXX, (p, b) -> p.set("eyeRoll4", b.readShort()));
        // FMB140
        // Permanent I/O Element
        register(69, null, (p, b) -> p.set(Position.KEY_GNSS_STATUS, b.readUnsignedByte()));
        register(6, null, (p, b) -> p.set(Position.KEY_ANALOG_INPUT2, b.readUnsignedShort() * 0.001));
        register(76, null, (p, b) -> p.set(Position.KEY_DALLAS_TEMP_ID1, b.readLong()));
        register(77, null, (p, b) -> p.set(Position.KEY_DALLAS_TEMP_ID2, b.readLong()));
        register(79, null, (p, b) -> p.set(Position.KEY_DALLAS_TEMP_ID3, b.readLong()));
        register(71, null, (p, b) -> p.set(Position.KEY_DALLAS_TEMP_ID4, b.readLong()));
        register(207, null, (p, b) -> p.set(Position.KEY_RFID, b.readLong()));
        register(201, null, (p, b) -> p.set(Position.KEY_LLS1_FUEL, b.readShort()));
        register(202, null, (p, b) -> p.set(Position.KEY_LLS1_TEMP, b.readByte()));
        register(203, null, (p, b) -> p.set(Position.KEY_LLS2_FUEL, b.readShort()));
        register(204, null, (p, b) -> p.set(Position.KEY_LLS2_TEMP, b.readByte()));
        register(210, null, (p, b) -> p.set(Position.KEY_LLS3_FUEL, b.readShort()));
        register(211, null, (p, b) -> p.set(Position.KEY_LLS3_TEMP, b.readByte()));
        register(212, null, (p, b) -> p.set(Position.KEY_LLS4_FUEL, b.readShort()));
        register(213, null, (p, b) -> p.set(Position.KEY_LLS4_TEMP, b.readByte()));
        register(214, null, (p, b) -> p.set(Position.KEY_LLS5_FUEL, b.readShort()));
        register(215, null, (p, b) -> p.set(Position.KEY_LLS5_TEMP, b.readByte()));
        register(15, null, (p, b) -> p.set(Position.KEY_ECO_SCORE, b.readUnsignedShort() * 0.01));
        register(238, null, (p, b) -> p.set(Position.KEY_USER_ID, b.readLong()));
        register(237, null, (p, b) -> p.set(Position.KEY_NETWORK_TYPE, b.readUnsignedByte()));
        register(5, null, (p, b) -> p.set(Position.KEY_PULSE_COUNT2, b.readUnsignedInt()));
        register(263, null, (p, b) -> p.set(Position.KEY_BT_STATUS, b.readUnsignedByte()));
        register(264, null, (p, b) -> p.set(Position.KEY_BARCODE_ID, b.readString(b.readUnsignedByte(), StandardCharsets.US_ASCII)));
        register(303, null, (p, b) -> p.set(Position.KEY_INSTANT_MOVEMENT, b.readUnsignedByte() > 0));
        register(327, null, (p, b) -> p.set(Position.KEY_UL202_FUEL, b.readShort() * 0.1));
        register(483, null, (p, b) -> p.set(Position.KEY_UL202_STATUS, b.readUnsignedByte()));
        register(380, null, (p, b) -> p.set(Position.KEY_OUTPUT3, b.readUnsignedByte() > 0));
        register(381, null, (p, b) -> p.set(Position.KEY_GROUND_SENSE, b.readUnsignedByte() > 0));
        register(387, null, (p, b) -> p.set(Position.KEY_ISO6709, b.readString(34, StandardCharsets.US_ASCII)));
        register(403, null, (p, b) -> p.set(Position.KEY_DRIVER_NAME, b.readString(35, StandardCharsets.US_ASCII)));
        register(404, null, (p, b) -> p.set(Position.KEY_DRIVER_LICENSE_TYPE, b.readUnsignedByte()));
        register(405, null, (p, b) -> p.set(Position.KEY_DRIVER_GENDER, b.readUnsignedByte()));
        register(406, null, (p, b) -> p.set(Position.KEY_DRIVER_CARD_ID, b.readUnsignedInt()));
        register(407, null, (p, b) -> p.set(Position.KEY_DRIVER_CARD_EXPIRY, b.readUnsignedShort()));
        register(408, null, (p, b) -> p.set(Position.KEY_DRIVER_ISSUE_PLACE, b.readUnsignedShort()));
        register(409, null, (p, b) -> p.set(Position.KEY_DRIVER_STATUS_EVENT, b.readUnsignedByte()));
        register(329, null, (p, b) -> p.set(Position.KEY_AIN_SPEED, b.readUnsignedShort()));
        register(500, null, (p, b) -> p.set(Position.KEY_VENDOR_NAME, b.readString(40, StandardCharsets.US_ASCII)));
        register(501, null, (p, b) -> p.set(Position.KEY_VEHICLE_NUMBER, b.readString(40, StandardCharsets.US_ASCII)));
        register(502, null, (p, b) -> p.set(Position.KEY_SPEED_SENSOR_STATUS, b.readUnsignedByte() > 0));
        register(637, null, (p, b) -> p.set(Position.KEY_WAKE_REASON, b.readUnsignedByte() > 0));
        register(10804, null, (p, b) -> p.set(Position.KEY_EYE_HUM1, b.readUnsignedByte()));
        register(10805, null, (p, b) -> p.set(Position.KEY_EYE_HUM2, b.readUnsignedByte()));
        register(10806, null, (p, b) -> p.set(Position.KEY_EYE_HUM3, b.readUnsignedByte()));
        register(10807, null, (p, b) -> p.set(Position.KEY_EYE_HUM4, b.readUnsignedByte()));
        register(10808, null, (p, b) -> p.set(Position.KEY_EYE_MAG1, b.readUnsignedByte() > 0));
        register(10809, null, (p, b) -> p.set(Position.KEY_EYE_MAG2, b.readUnsignedByte() > 0));
        register(10810, null, (p, b) -> p.set(Position.KEY_EYE_MAG3, b.readUnsignedByte() > 0));
        register(10811, null, (p, b) -> p.set(Position.KEY_EYE_MAG4, b.readUnsignedByte() > 0));
        register(10812, null, (p, b) -> p.set(Position.KEY_EYE_MOVE1, b.readUnsignedByte() > 0));
        register(10813, null, (p, b) -> p.set(Position.KEY_EYE_MOVE2, b.readUnsignedByte() > 0));
        register(10814, null, (p, b) -> p.set(Position.KEY_EYE_MOVE3, b.readUnsignedByte() > 0));
        register(10815, null, (p, b) -> p.set(Position.KEY_EYE_MOVE4, b.readUnsignedByte() > 0));
        register(10816, null, (p, b) -> p.set(Position.KEY_EYE_PITCH1, b.readByte()));
        register(10817, null, (p, b) -> p.set(Position.KEY_EYE_PITCH2, b.readByte()));
        register(10818, null, (p, b) -> p.set(Position.KEY_EYE_PITCH3, b.readByte()));
        register(10819, null, (p, b) -> p.set(Position.KEY_EYE_PITCH4, b.readByte()));
        register(10820, null, (p, b) -> p.set(Position.KEY_EYE_LOW_BAT1, b.readUnsignedByte() > 0));
        register(10821, null, (p, b) -> p.set(Position.KEY_EYE_LOW_BAT2, b.readUnsignedByte() > 0));
        register(10822, null, (p, b) -> p.set(Position.KEY_EYE_LOW_BAT3, b.readUnsignedByte() > 0));
        register(10823, null, (p, b) -> p.set(Position.KEY_EYE_LOW_BAT4, b.readUnsignedByte() > 0));
        register(10824, null, (p, b) -> p.set(Position.KEY_EYE_BAT_VOLT1, b.readUnsignedShort() * 0.001));
        register(10825, null, (p, b) -> p.set(Position.KEY_EYE_BAT_VOLT2, b.readUnsignedShort() * 0.001));
        register(10826, null, (p, b) -> p.set(Position.KEY_EYE_BAT_VOLT3, b.readUnsignedShort() * 0.001));
        register(10827, null, (p, b) -> p.set(Position.KEY_EYE_BAT_VOLT4, b.readUnsignedShort() * 0.001));
        register(10836, null, (p, b) -> p.set(Position.KEY_EYE_MOVE_COUNT1, b.readUnsignedShort()));
        register(10837, null, (p, b) -> p.set(Position.KEY_EYE_MOVE_COUNT2, b.readUnsignedShort()));
        register(10838, null, (p, b) -> p.set(Position.KEY_EYE_MOVE_COUNT3, b.readUnsignedShort()));
        register(10839, null, (p, b) -> p.set(Position.KEY_EYE_MOVE_COUNT4, b.readUnsignedShort()));
        register(10840, null, (p, b) -> p.set(Position.KEY_EYE_MAG_COUNT1, b.readUnsignedShort()));
        register(10841, null, (p, b) -> p.set(Position.KEY_EYE_MAG_COUNT2, b.readUnsignedShort()));
        register(10842, null, (p, b) -> p.set(Position.KEY_EYE_MAG_COUNT3, b.readUnsignedShort()));
        register(10843, null, (p, b) -> p.set(Position.KEY_EYE_MAG_COUNT4, b.readUnsignedShort()));
        register(383, null, (p, b) -> p.set(Position.KEY_AXL_CALIB_STATUS, b.readUnsignedByte()));
        register(451, null, (p, b) -> p.set(Position.KEY_BLE_RFID1, ByteBufUtil.hexDump(b.readSlice(8))));
        register(452, null, (p, b) -> p.set(Position.KEY_BLE_RFID2, ByteBufUtil.hexDump(b.readSlice(8))));
        register(453, null, (p, b) -> p.set(Position.KEY_BLE_RFID3, ByteBufUtil.hexDump(b.readSlice(8))));
        register(454, null, (p, b) -> p.set(Position.KEY_BLE_RFID4, ByteBufUtil.hexDump(b.readSlice(8))));
        register(455, null, (p, b) -> p.set(Position.KEY_BLE_BTN1_STATE1, b.readUnsignedByte() > 0));
        register(456, null, (p, b) -> p.set(Position.KEY_BLE_BTN1_STATE2, b.readUnsignedByte() > 0));
        register(457, null, (p, b) -> p.set(Position.KEY_BLE_BTN1_STATE3, b.readUnsignedByte() > 0));
        register(458, null, (p, b) -> p.set(Position.KEY_BLE_BTN1_STATE4, b.readUnsignedByte() > 0));
        register(459, null, (p, b) -> p.set(Position.KEY_BLE_BTN2_STATE1, b.readUnsignedByte() > 0));
        register(460, null, (p, b) -> p.set(Position.KEY_BLE_BTN2_STATE2, b.readUnsignedByte() > 0));
        register(461, null, (p, b) -> p.set(Position.KEY_BLE_BTN2_STATE3, b.readUnsignedByte() > 0));
        register(462, null, (p, b) -> p.set(Position.KEY_BLE_BTN2_STATE4, b.readUnsignedByte() > 0));
        register(622, null, (p, b) -> p.set(Position.KEY_FREQ_DIN1, b.readUnsignedShort() * 0.1));
        register(623, null, (p, b) -> p.set(Position.KEY_FREQ_DIN2, b.readUnsignedShort() * 0.1));
        register(1148, null, (p, b) -> p.set(Position.KEY_CONNECTIVITY_QUALITY, b.readUnsignedInt()));
        register(155, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_01, b.readUnsignedByte()));
        register(156, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_02, b.readUnsignedByte()));
        register(157, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_03, b.readUnsignedByte()));
        register(158, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_04, b.readUnsignedByte()));
        register(159, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_05, b.readUnsignedByte()));
        register(61,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_06, b.readUnsignedByte()));
        register(62,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_07, b.readUnsignedByte()));
        register(63,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_08, b.readUnsignedByte()));
        register(64,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_09, b.readUnsignedByte()));
        register(65,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_10, b.readUnsignedByte()));
        register(70,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_11, b.readUnsignedByte()));
        register(88,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_12, b.readUnsignedByte()));
        register(91,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_13, b.readUnsignedByte()));
        register(92,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_14, b.readUnsignedByte()));
        register(93,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_15, b.readUnsignedByte()));
        register(94,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_16, b.readUnsignedByte()));
        register(95,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_17, b.readUnsignedByte()));
        register(96,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_18, b.readUnsignedByte()));
        register(97,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_19, b.readUnsignedByte()));
        register(98,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_20, b.readUnsignedByte()));
        register(99,  null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_21, b.readUnsignedByte()));
        register(153, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_22, b.readUnsignedByte()));
        register(154, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_23, b.readUnsignedByte()));
        register(190, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_24, b.readUnsignedByte()));
        register(191, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_25, b.readUnsignedByte()));
        register(192, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_26, b.readUnsignedByte()));
        register(193, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_27, b.readUnsignedByte()));
        register(194, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_28, b.readUnsignedByte()));
        register(195, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_29, b.readUnsignedByte()));
        register(196, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_30, b.readUnsignedByte()));
        register(197, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_31, b.readUnsignedByte()));
        register(198, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_32, b.readUnsignedByte()));
        register(208, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_33, b.readUnsignedByte()));
        register(209, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_34, b.readUnsignedByte()));
        register(216, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_35, b.readUnsignedByte()));
        register(217, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_36, b.readUnsignedByte()));
        register(218, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_37, b.readUnsignedByte()));
        register(219, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_38, b.readUnsignedByte()));
        register(220, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_39, b.readUnsignedByte()));
        register(221, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_40, b.readUnsignedByte()));
        register(222, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_41, b.readUnsignedByte()));
        register(223, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_42, b.readUnsignedByte()));
        register(224, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_43, b.readUnsignedByte()));
        register(225, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_44, b.readUnsignedByte()));
        register(226, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_45, b.readUnsignedByte()));
        register(227, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_46, b.readUnsignedByte()));
        register(228, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_47, b.readUnsignedByte()));
        register(229, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_48, b.readUnsignedByte()));
        register(230, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_49, b.readUnsignedByte()));
        register(231, null, (p, b) -> p.set(Position.KEY_GEOFENCE_ZONE_50, b.readUnsignedByte()));
        register(250, null, (p, b) -> p.set(Position.KEY_TRIP, b.readUnsignedByte()));
        register(255, null, (p, b) -> p.set(Position.KEY_OVERSPEED, b.readUnsignedByte()));
        register(257, null, (p, b) -> {
            int length = b.readUnsignedByte();
            p.set(Position.KEY_CRASH_TRACE, ByteBufUtil.hexDump(b.readSlice(length)));
        });
        register(285, null, (p, b) -> p.set(Position.KEY_ALCOHOL, b.readUnsignedShort() / 1000.0));
        register(248, null, (p, b) -> p.set(Position.KEY_IMMOBILIZER, b.readUnsignedByte()));
        register(254, null, (p, b) -> p.set(Position.KEY_GREEN_VALUE, b.readUnsignedByte()));
        register(14, null, (p, b) -> p.set(Position.KEY_ICCID2, b.readLong()));
        register(243, null, (p, b) -> p.set(Position.KEY_GREEN_DURATION, b.readUnsignedShort()));
        register(258, null, (p, b) -> p.set(Position.KEY_ECO_MAX, b.readLong()));
        register(259, null, (p, b) -> p.set(Position.KEY_ECO_AVG, b.readLong()));
        register(260, null, (p, b) -> p.set(Position.KEY_ECO_DURATION, b.readUnsignedShort()));
        register(283, null, (p, b) -> p.set(Position.KEY_DRIVING_STATE, b.readUnsignedByte()));
        register(284, null, (p, b) -> p.set(Position.KEY_DRIVING_RECORDS, b.readUnsignedShort()));
        register(317, null, (p, b) -> p.set(Position.KEY_CRASH_COUNT, b.readUnsignedByte()));
        register(318, null, (p, b) -> p.set(Position.KEY_GNSS_JAMMING, b.readUnsignedByte()));
        register(391, null, (p, b) -> p.set(Position.KEY_PRIVATE_MODE, b.readUnsignedByte()));
        register(449, null, (p, b) -> p.set(Position.KEY_IGNITION_ON_TIME, b.readUnsignedInt()));
        register(1412, null, (p, b) -> p.set(Position.KEY_MOTO_FALL, b.readUnsignedByte()));
        register(256, null, (p, b) -> p.set(Position.KEY_VIN, b.readCharSequence(17, StandardCharsets.US_ASCII).toString()));
        // OBD Parameters
        register(34, null, (p, b) -> p.set(Position.KEY_FUEL_PRESSURE, b.readUnsignedShort()));
        register(35, null, (p, b) -> p.set(Position.KEY_MAP, b.readUnsignedByte()));
        register(37, null, (p, b) -> p.set(Position.KEY_OBD_SPEED, b.readUnsignedByte()));
        register(38, null, (p, b) -> p.set(Position.KEY_TIMING_ADVANCE, b.readByte()));
        register(39, null, (p, b) -> p.set(Position.KEY_INTAKE_TEMP, b.readByte()));
        register(40, null, (p, b) -> p.set(Position.KEY_MAF, b.readUnsignedShort() * 0.01));
        register(41, null, (p, b) -> p.set(Position.KEY_THROTTLE, b.readUnsignedByte()));
        register(42, null, (p, b) -> p.set(Position.KEY_RUNTIME, b.readUnsignedShort()));
        register(44, null, (p, b) -> p.set(Position.KEY_REL_FUEL_PRESSURE, b.readUnsignedShort() * 0.1));
        register(45, null, (p, b) -> p.set(Position.KEY_DIR_FUEL_PRESSURE, b.readUnsignedShort() * 10));
        register(46, null, (p, b) -> p.set(Position.KEY_CMD_EGR, b.readUnsignedByte()));
        register(47, null, (p, b) -> p.set(Position.KEY_EGR_ERROR, b.readByte()));
        register(48, null, (p, b) -> p.set(Position.KEY_FUEL_LEVEL, b.readUnsignedByte()));
        register(49, null, (p, b) -> p.set(Position.KEY_DIST_CLEAR, b.readUnsignedShort()));
        register(50, null, (p, b) -> p.set(Position.KEY_BARO_PRESSURE, b.readUnsignedByte()));
        register(51, null, (p, b) -> p.set(Position.KEY_MODULE_VOLTAGE, b.readUnsignedShort() * 0.001));
        // OBD elements
        register(52, fmbXXX, (p, b) -> p.set(Position.KEY_ABS_LOAD, b.readUnsignedShort() * 0.01));
        register(759, fmbXXX, (p, b) -> p.set(Position.KEY_FUEL_TYPE, b.readUnsignedByte()));
        register(53, fmbXXX, (p, b) -> p.set(Position.KEY_AMBIENT_AIR_TEMP, b.readByte()));
        register(54, fmbXXX, (p, b) -> p.set(Position.KEY_MIL_RUNTIME, b.readUnsignedShort()));
        register(55, fmbXXX, (p, b) -> p.set(Position.KEY_TIME_SINCE_CLEAR, b.readUnsignedShort()));
        register(56, fmbXXX, (p, b) -> p.set(Position.KEY_FUEL_RAIL_PRESS, b.readUnsignedShort() * 10));
        register(58, fmbXXX, (p, b) -> p.set(Position.KEY_ENGINE_OIL_TEMP, b.readUnsignedByte()));
        register(59, fmbXXX, (p, b) -> p.set(Position.KEY_INJECTION_TIMING, b.readShort() * 0.01));
        register(540, fmbXXX, (p, b) -> p.set(Position.KEY_THROTTLE_POS_GROUP, b.readUnsignedByte()));
        register(541, fmbXXX, (p, b) -> p.set(Position.KEY_EQUIV_RATIO, b.readUnsignedByte() * 0.01));
        register(542, fmbXXX, (p, b) -> p.set(Position.KEY_MAP2, b.readUnsignedShort()));
        register(543, fmbXXX, (p, b) -> p.set(Position.KEY_HYBRID_VOLTAGE, b.readUnsignedShort()));
        register(544, fmbXXX, (p, b) -> p.set(Position.KEY_HYBRID_CURRENT, b.readShort()));
        register(60, fmbXXX, (p, b) -> p.set(Position.KEY_FUEL_RATE, b.readUnsignedShort() * 0.01));

        // OBD OEM elements
        register(389, fmbXXX, (p, b) -> p.set(Position.KEY_OEM_TOTAL_MILEAGE, b.readUnsignedInt()));
        register(390, fmbXXX, (p, b) -> p.set(Position.KEY_OEM_FUEL_LEVEL, b.readUnsignedInt() * 0.1));
        register(402, fmbXXX, (p, b) -> p.set(Position.KEY_OEM_DISTANCE_SERVICE, b.readUnsignedInt()));
        register(411, fmbXXX, (p, b) -> p.set(Position.KEY_OEM_BATTERY_CHARGE, b.readUnsignedByte()));
        register(755, fmbXXX, (p, b) -> p.set(Position.KEY_OEM_REMAINING_DIST, b.readUnsignedShort()));
        register(1151, fmbXXX, (p, b) -> p.set(Position.KEY_OEM_BATTERY_HEALTH, b.readUnsignedShort()));
        register(1152, fmbXXX, (p, b) -> p.set(Position.KEY_OEM_BATTERY_TEMP, b.readShort()));

        // BLE Sensors
        register(29, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_BATTERY1, b.readUnsignedByte()));
        register(20, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_BATTERY2, b.readUnsignedByte()));
        register(22, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_BATTERY3, b.readUnsignedByte()));
        // BLE Battery
        register(23, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_BATTERY4, b.readUnsignedByte()));

        // BLE Humidity
        register(86, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_HUMIDITY1, b.readUnsignedShort() * 0.1));
        register(104, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_HUMIDITY2, b.readUnsignedShort() * 0.1));
        register(106, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_HUMIDITY3, b.readUnsignedShort() * 0.1));
        register(108, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_HUMIDITY4, b.readUnsignedShort() * 0.1));

        // BLE Fuel Level
        register(270, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_FUEL_LEVEL1, b.readUnsignedShort()));
        register(273, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_FUEL_LEVEL2, b.readUnsignedShort()));
        register(276, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_FUEL_LEVEL3, b.readUnsignedShort()));
        register(279, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_FUEL_LEVEL4, b.readUnsignedShort()));
        register(281, fmbXXX, (p, b) -> p.set(Position.KEY_FAULT_CODES, b.readString(b.readUnsignedByte(), StandardCharsets.US_ASCII)));

        // BLE Fuel Frequency
        register(306, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_FUEL_FREQ1, b.readUnsignedInt()));
        register(307, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_FUEL_FREQ2, b.readUnsignedInt()));
        register(308, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_FUEL_FREQ3, b.readUnsignedInt()));
        register(309, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_FUEL_FREQ4, b.readUnsignedInt()));

        // BLE Luminosity
        register(335, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_LUMINOSITY1, b.readUnsignedShort()));
        register(336, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_LUMINOSITY2, b.readUnsignedShort()));
        register(337, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_LUMINOSITY3, b.readUnsignedShort()));
        register(338, fmbXXX, (p, b) -> p.set(Position.KEY_BLE_LUMINOSITY4, b.readUnsignedShort()));

        // BLE Custom 1
        register(331, fmbXXX, (p, b) -> p.set(Position.KEY_BLE1_CUSTOM1,  b.readString(b.readUnsignedByte(), StandardCharsets.US_ASCII)));
        register(463, fmbXXX, (p, b) -> p.set(Position.KEY_BLE1_CUSTOM2, b.readUnsignedInt()));
        register(464, fmbXXX, (p, b) -> p.set(Position.KEY_BLE1_CUSTOM3, b.readUnsignedInt()));
        register(465, fmbXXX, (p, b) -> p.set(Position.KEY_BLE1_CUSTOM4, b.readUnsignedInt()));
        register(466, fmbXXX, (p, b) -> p.set(Position.KEY_BLE1_CUSTOM5, b.readUnsignedInt()));

        // BLE Custom 2
        register(332, fmbXXX, (p, b) -> p.set(Position.KEY_BLE2_CUSTOM1,  b.readString(b.readUnsignedByte(), StandardCharsets.US_ASCII)));
        register(467, fmbXXX, (p, b) -> p.set(Position.KEY_BLE2_CUSTOM2, b.readUnsignedInt()));
        register(468, fmbXXX, (p, b) -> p.set(Position.KEY_BLE2_CUSTOM3, b.readUnsignedInt()));
        register(469, fmbXXX, (p, b) -> p.set(Position.KEY_BLE2_CUSTOM4, b.readUnsignedInt()));
        register(470, fmbXXX, (p, b) -> p.set(Position.KEY_BLE2_CUSTOM5, b.readUnsignedInt()));

        // BLE Custom 3
        register(333, fmbXXX, (p, b) -> p.set(Position.KEY_BLE3_CUSTOM1,  b.readString(b.readUnsignedByte(), StandardCharsets.US_ASCII)));
        register(471, fmbXXX, (p, b) -> p.set(Position.KEY_BLE3_CUSTOM2, b.readUnsignedInt()));
        register(472, fmbXXX, (p, b) -> p.set(Position.KEY_BLE3_CUSTOM3, b.readUnsignedInt()));
        register(473, fmbXXX, (p, b) -> p.set(Position.KEY_BLE3_CUSTOM4, b.readUnsignedInt()));
        register(474, fmbXXX, (p, b) -> p.set(Position.KEY_BLE3_CUSTOM5, b.readUnsignedInt()));
        // BLE 4 Custom
        register(334, fmbXXX, (p, b) -> p.set(Position.KEY_BLE4_CUSTOM1, ByteBufUtil.hexDump(b.readSlice(1))));
        register(475, fmbXXX, (p, b) -> p.set(Position.KEY_BLE4_CUSTOM2, b.readUnsignedInt()));
        register(476, fmbXXX, (p, b) -> p.set(Position.KEY_BLE4_CUSTOM3, b.readUnsignedInt()));
        register(477, fmbXXX, (p, b) -> p.set(Position.KEY_BLE4_CUSTOM4, b.readUnsignedInt()));
        register(478, fmbXXX, (p, b) -> p.set(Position.KEY_BLE4_CUSTOM5, b.readUnsignedInt()));
        // CAN
        register(90,  fmbXXX, (p, b) -> p.set(Position.KEY_DOOR_STATUS, b.readUnsignedShort()));
        register(100, fmbXXX, (p, b) -> p.set(Position.KEY_PROGRAM_NUMBER, b.readUnsignedInt()));
        register(101, fmbXXX, (p, b) -> p.set(Position.KEY_MODULE_ID_8B, ByteBufUtil.hexDump(b.readSlice(8))));
        register(388, fmbXXX, (p, b) -> p.set(Position.KEY_MODULE_ID_17B, ByteBufUtil.hexDump(b.readSlice(17))));
        register(102, fmbXXX, (p, b) -> p.set(Position.KEY_ENGINE_WORKTIME, b.readUnsignedInt()));
        register(103, fmbXXX, (p, b) -> p.set(Position.KEY_ENGINE_WORKTIME_COUNTED, b.readUnsignedInt()));
        register(105, fmbXXX, (p, b) -> p.set(Position.KEY_TOTAL_MILEAGE_COUNTED, b.readUnsignedInt()));
        register(107, fmbXXX, (p, b) -> p.set(Position.KEY_FUEL_CONSUMED_COUNTED, b.readUnsignedInt() * 0.1));
        register(111, fmbXXX, (p, b) -> p.set(Position.KEY_ADBLUE_PERCENT, b.readUnsignedByte()));
        register(112, fmbXXX, (p, b) -> p.set(Position.KEY_ADBLUE_LEVEL, b.readUnsignedShort() * 0.1));
        register(114, fmbXXX, (p, b) -> p.set(Position.KEY_ENGINE_LOAD, b.readUnsignedByte()));
        register(118, fmbXXX, (p, b) -> p.set(Position.KEY_AXLE1_LOAD, b.readUnsignedShort()));
        register(119, fmbXXX, (p, b) -> p.set(Position.KEY_AXLE2_LOAD, b.readUnsignedShort()));
        register(120, fmbXXX, (p, b) -> p.set(Position.KEY_AXLE3_LOAD, b.readUnsignedShort()));
        register(121, fmbXXX, (p, b) -> p.set(Position.KEY_AXLE4_LOAD, b.readUnsignedShort()));
        register(122, fmbXXX, (p, b) -> p.set(Position.KEY_AXLE5_LOAD, b.readUnsignedShort()));
        register(123, fmbXXX, (p, b) -> p.set(Position.KEY_CONTROL_FLAGS, b.readUnsignedInt()));
        register(124, fmbXXX, (p, b) -> p.set(Position.KEY_AGR_FLAGS, b.readLong())); // 8 bytes
        register(125, fmbXXX, (p, b) -> p.set(Position.KEY_HARVEST_TIME, b.readUnsignedInt()));
        register(126, fmbXXX, (p, b) -> p.set(Position.KEY_HARVEST_AREA, b.readUnsignedInt()));
        register(127, fmbXXX, (p, b) -> p.set(Position.KEY_MOWING_EFFICIENCY, b.readUnsignedInt()));       // m2/h
        register(128, fmbXXX, (p, b) -> p.set(Position.KEY_GRAIN_VOLUME, b.readUnsignedInt()));            // kg
        register(129, fmbXXX, (p, b) -> p.set(Position.KEY_GRAIN_MOISTURE, b.readUnsignedShort()));        // %
        register(130, fmbXXX, (p, b) -> p.set(Position.KEY_HARVEST_DRUM_RPM, b.readUnsignedShort()));      // rpm
        register(131, fmbXXX, (p, b) -> p.set(Position.KEY_HARVEST_DRUM_GAP, b.readUnsignedByte()));       // mm
        register(132, fmbXXX, (p, b) -> p.set(Position.KEY_SECURITY_FLAGS, b.readLong()));                 // 8 bytes
        register(133, fmbXXX, (p, b) -> p.set(Position.KEY_TACHO_TOTAL_DISTANCE, b.readUnsignedInt()));    // m
        register(134, fmbXXX, (p, b) -> p.set(Position.KEY_TRIP_DISTANCE, b.readUnsignedInt()));           // m
        register(135, fmbXXX, (p, b) -> p.set(Position.KEY_TACHO_SPEED, b.readUnsignedShort()));           // km/h
        register(136, fmbXXX, (p, b) -> p.set(Position.KEY_TACHO_CARD_PRESENT, b.readUnsignedByte()));
        register(137, fmbXXX, (p, b) -> p.set(Position.KEY_DRIVER1_STATES, b.readUnsignedByte()));
        register(138, fmbXXX, (p, b) -> p.set(Position.KEY_DRIVER2_STATES, b.readUnsignedByte()));
        register(139, fmbXXX, (p, b) -> p.set(Position.KEY_DRIVER1_CONT_DRIVE, b.readUnsignedShort()));    // minutes
        register(140, fmbXXX, (p, b) -> p.set(Position.KEY_DRIVER2_CONT_DRIVE, b.readUnsignedShort()));    // minutes
        register(141, fmbXXX, (p, b) -> p.set(Position.KEY_DRIVER1_BREAK_TIME, b.readUnsignedShort()));    // minutes
        register(142, fmbXXX, (p, b) -> p.set(Position.KEY_DRIVER2_BREAK_TIME, b.readUnsignedShort()));    // minutes
        register(143, fmbXXX, (p, b) -> p.set(Position.KEY_DRIVER1_ACTIVITY, b.readUnsignedShort()));      // minutes
        register(144, fmbXXX, (p, b) -> p.set(Position.KEY_DRIVER2_ACTIVITY, b.readUnsignedShort()));      // minutes
        register(145, fmbXXX, (p, b) -> p.set(Position.KEY_DRIVER1_CUM_DRIVE, b.readUnsignedShort()));     // minutes
        register(146, fmbXXX, (p, b) -> p.set(Position.KEY_DRIVER2_CUM_DRIVE, b.readUnsignedShort()));     // minutes
        register(147, fmbXXX, (p, b) -> p.set(Position.KEY_DRIVER1_ID_HIGH, b.readLong()));                // 8 bytes
        register(148, fmbXXX, (p, b) -> p.set(Position.KEY_DRIVER1_ID_LOW, b.readLong()));                 // 8 bytes
        register(149, fmbXXX, (p, b) -> p.set(Position.KEY_DRIVER2_ID_HIGH, b.readLong()));                // 8 bytes
        register(150, fmbXXX, (p, b) -> p.set(Position.KEY_DRIVER2_ID_LOW, b.readLong()));                 // 8 bytes
        register(151, fmbXXX, (p, b) -> p.set(Position.KEY_BATTERY_TEMP, b.readShort() * 0.1));            // °C
        register(152, fmbXXX, (p, b) -> p.set(Position.KEY_HV_BATTERY_LEVEL, b.readUnsignedByte()));       // %
        register(160, fmbXXX, (p, b) -> p.set(Position.KEY_DTC_COUNT, b.readUnsignedByte()));
        register(161, fmbXXX, (p, b) -> p.set(Position.KEY_ARM_SLOPE, b.readShort()));                     // °
        register(162, fmbXXX, (p, b) -> p.set(Position.KEY_ARM_ROTATION, b.readShort()));                  // °
        register(163, fmbXXX, (p, b) -> p.set(Position.KEY_ARM_EJECT, b.readUnsignedShort()));             // m
        register(164, fmbXXX, (p, b) -> p.set(Position.KEY_ARM_DISTANCE, b.readUnsignedShort()));          // m
        register(165, fmbXXX, (p, b) -> p.set(Position.KEY_ARM_HEIGHT, b.readUnsignedShort()));
        register(166, fmbXXX, (p, b) -> p.set(Position.KEY_DRILL_RPM, b.readUnsignedShort()));
        register(167, fmbXXX, (p, b) -> p.set(Position.KEY_SALT_SQ_METER, b.readUnsignedShort()));
        register(168, fmbXXX, (p, b) -> p.set(Position.KEY_BATTERY_VOLTAGE, b.readUnsignedShort() * 0.001));
        register(169, fmbXXX, (p, b) -> p.set(Position.KEY_FINE_SALT, b.readUnsignedInt()));
        register(170, fmbXXX, (p, b) -> p.set(Position.KEY_COARSE_SALT, b.readUnsignedInt()));
        register(171, fmbXXX, (p, b) -> p.set(Position.KEY_DIMIX, b.readUnsignedInt()));
        register(172, fmbXXX, (p, b) -> p.set(Position.KEY_COARSE_CALCIUM, b.readUnsignedInt()));
        register(173, fmbXXX, (p, b) -> p.set(Position.KEY_CALCIUM_CHLORIDE, b.readUnsignedInt()));
        register(174, fmbXXX, (p, b) -> p.set(Position.KEY_SODIUM_CHLORIDE, b.readUnsignedInt()));
        register(176, fmbXXX, (p, b) -> p.set(Position.KEY_MAGNESIUM_CHLORIDE, b.readUnsignedInt()));
        register(177, fmbXXX, (p, b) -> p.set(Position.KEY_GRAVEL, b.readUnsignedInt()));
        register(178, fmbXXX, (p, b) -> p.set(Position.KEY_SAND, b.readUnsignedInt()));
        register(183, fmbXXX, (p, b) -> p.set(Position.KEY_WIDTH_LEFT, b.readUnsignedShort()));
        register(184, fmbXXX, (p, b) -> p.set(Position.KEY_WIDTH_RIGHT, b.readUnsignedShort()));
        register(185, fmbXXX, (p, b) -> p.set(Position.KEY_SALT_HOURS, b.readUnsignedInt()));
        register(186, fmbXXX, (p, b) -> p.set(Position.KEY_SALT_DISTANCE, b.readUnsignedInt()));
        register(187, fmbXXX, (p, b) -> p.set(Position.KEY_LOAD_WEIGHT, b.readUnsignedInt()));
        register(188, fmbXXX, (p, b) -> p.set(Position.KEY_RETARDER_LOAD, b.readUnsignedByte()));
        register(189, fmbXXX, (p, b) -> p.set(Position.KEY_CRUISE_TIME, b.readUnsignedInt()));

        // 282
        register(282, fmbXXX, (p, b) -> p.set(Position.KEY_DTC_CODES, b.readString(b.readUnsignedByte(), StandardCharsets.US_ASCII)));

        // 304–305
        register(304, fmbXXX, (p, b) -> p.set(Position.KEY_RANGE_BATTERY, b.readUnsignedInt()));
        register(305, fmbXXX, (p, b) -> p.set(Position.KEY_RANGE_FUEL, b.readUnsignedInt()));

        // 325
        register(325, fmbXXX, (p, b) -> p.set(Position.KEY_VIN, b.readCharSequence(17, StandardCharsets.US_ASCII).toString()));

        // 517–521
        register(517, fmbXXX, (p, b) -> p.set(Position.KEY_SECURITY_FLAGS_P4, ByteBufUtil.hexDump(b.readSlice(8))));
        register(518, fmbXXX, (p, b) -> p.set(Position.KEY_CONTROL_FLAGS_P4, ByteBufUtil.hexDump(b.readSlice(8))));
        register(519, fmbXXX, (p, b) -> p.set(Position.KEY_INDICATOR_FLAGS_P4, ByteBufUtil.hexDump(b.readSlice(8))));
        register(520, fmbXXX, (p, b) -> p.set(Position.KEY_AGRICULTURE_FLAGS_P4, ByteBufUtil.hexDump(b.readSlice(8))));
        register(521, fmbXXX, (p, b) -> p.set(Position.KEY_UTILITY_FLAGS_P4, ByteBufUtil.hexDump(b.readSlice(8))));
        // Cistern / LNG / LPG
        register(522, fmbXXX, (p, b) -> p.set(Position.KEY_CISTERN_STATE_FLAGS_P4, b.readLong()));
        register(855, fmbXXX, (p, b) -> p.set(Position.KEY_LNG_USED, b.readUnsignedInt()));
        register(856, fmbXXX, (p, b) -> p.set(Position.KEY_LNG_USED_COUNTED, b.readUnsignedInt()));
        register(857, fmbXXX, (p, b) -> p.set(Position.KEY_LNG_LEVEL_PROC, b.readUnsignedShort()));
        register(858, fmbXXX, (p, b) -> p.set(Position.KEY_LNG_LEVEL_KG, b.readUnsignedShort()));
        register(1100, fmbXXX, (p, b) -> p.set(Position.KEY_LPG_USED, b.readUnsignedInt()));
        register(1101, fmbXXX, (p, b) -> p.set(Position.KEY_LPG_USED_COUNTED, b.readUnsignedInt()));
        register(1102, fmbXXX, (p, b) -> p.set(Position.KEY_LPG_LEVEL_PROC, b.readUnsignedShort()));
        register(1103, fmbXXX, (p, b) -> p.set(Position.KEY_LPG_LEVEL_LITERS, b.readUnsignedShort()));

        // SSF flags
        register(898, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_IGNITION, b.readUnsignedByte() == 1));
        register(652, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_KEY_IN_IGNITION, b.readUnsignedByte() == 1));
        register(899, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_WEBASO, b.readUnsignedByte() == 1));
        register(900, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_ENGINE_WORKING, b.readUnsignedByte() == 1));
        register(901, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_STANDALONE_ENGINE, b.readUnsignedByte() == 1));
        register(902, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_READY_TO_DRIVE, b.readUnsignedByte() == 1));
        register(903, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_ENGINE_WORKING_CNG, b.readUnsignedByte() == 1));
        register(904, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_WORK_MODE, b.readUnsignedByte()));
        register(905, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_OPERATOR, b.readUnsignedByte() == 1));
        register(906, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_INTERLOCK, b.readUnsignedByte() == 1));
        register(907, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_ENGINE_LOCK_ACTIVE, b.readUnsignedByte() == 1));
        register(908, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_REQUEST_LOCK_ENGINE, b.readUnsignedByte() == 1));
        register(653, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_HANDBRAKE_ACTIVE, b.readUnsignedByte() == 1));
        register(910, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_FOOTBRAKE_ACTIVE, b.readUnsignedByte() == 1));
        register(911, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_CLUTCH_PUSHED, b.readUnsignedByte() == 1));
        register(912, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_HAZARD_WARNING, b.readUnsignedByte() == 1));
        register(654, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_FRONT_LEFT_DOOR, b.readUnsignedByte() == 1));
        register(655, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_FRONT_RIGHT_DOOR, b.readUnsignedByte() == 1));
        register(656, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_REAR_LEFT_DOOR, b.readUnsignedByte() == 1));
        register(657, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_REAR_RIGHT_DOOR, b.readUnsignedByte() == 1));
        register(658, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_TRUNK_DOOR, b.readUnsignedByte() == 1));
        register(913, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_ENGINE_COVER, b.readUnsignedByte() == 1));
        // SSF extended
        register(909, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_ROOF_OPEN, b.readUnsignedByte() == 1));
        register(914, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_CHARGING_WIRE, b.readUnsignedByte() == 1));
        register(915, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_BATTERY_CHARGING, b.readUnsignedByte() == 1));
        register(916, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_ELECTRIC_ENGINE, b.readUnsignedByte() == 1));
        register(917, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_CAR_CLOSED_FACTORY, b.readUnsignedByte() == 1));
        register(918, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_FACTORY_ALARM_ACTUATED, b.readUnsignedByte() == 1));
        register(919, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_FACTORY_ALARM_EMULATED, b.readUnsignedByte() == 1));
        register(920, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_SIGNAL_CLOSE_FACTORY, b.readUnsignedByte() == 1));
        register(921, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_SIGNAL_OPEN_FACTORY, b.readUnsignedByte() == 1));
        register(922, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_REARMING_SIGNAL, b.readUnsignedByte() == 1));
        register(923, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_TRUNK_REMOTE, b.readUnsignedByte() == 1));
        register(924, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_CAN_SLEEP, b.readUnsignedByte() == 1));
        register(925, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_FACTORY_REMOTE_3X, b.readUnsignedByte() == 1));
        register(926, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_FACTORY_ARMED, b.readUnsignedByte() == 1));
        register(660, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_PARKING_GEAR, b.readUnsignedByte() == 1));
        register(661, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_REVERSE_GEAR, b.readUnsignedByte() == 1));
        register(659, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_NEUTRAL_GEAR, b.readUnsignedByte() == 1));
        register(927, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_DRIVE_ACTIVE, b.readUnsignedByte() == 1));
        register(1083, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_ENGINE_WORKING_DUALFUEL, b.readUnsignedByte() == 1));
        register(1084, fmbXXX, (p, b) -> p.set(Position.KEY_SSF_ENGINE_WORKING_LPG, b.readUnsignedByte() == 1));

        // CSF flags
        register(928, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_PARKING_LIGHTS, b.readUnsignedByte() == 1));
        register(929, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_DIPPED_HEADLIGHTS, b.readUnsignedByte() == 1));
        register(930, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_FULL_BEAM_HEADLIGHTS, b.readUnsignedByte() == 1));
        register(931, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_REAR_FOG_LIGHTS, b.readUnsignedByte() == 1));
        register(932, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_FRONT_FOG_LIGHTS, b.readUnsignedByte() == 1));
        register(933, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_ADDITIONAL_FRONT_LIGHTS, b.readUnsignedByte() == 1));
        register(934, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_ADDITIONAL_REAR_LIGHTS, b.readUnsignedByte() == 1));
        register(935, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_LIGHT_SIGNAL, b.readUnsignedByte() == 1));
        register(936, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_AIR_CONDITIONING, b.readUnsignedByte() == 1));
        register(937, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_CRUISE_CONTROL, b.readUnsignedByte() == 1));
        register(938, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_AUTOMATIC_RETARDER, b.readUnsignedByte() == 1));
        register(939, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_MANUAL_RETARDER, b.readUnsignedInt()));
        register(940, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_DRIVER_SEATBELT, b.readUnsignedInt()));
        register(941, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_FRONT_DRIVER_SEATBELT, b.readUnsignedInt()));
        register(942, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_LEFT_DRIVER_SEATBELT, b.readUnsignedInt()));
        register(943, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_RIGHT_DRIVER_SEATBELT, b.readUnsignedInt()));
        register(944, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_CENTRE_DRIVER_SEATBELT, b.readUnsignedInt()));
        register(945, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_FRONT_PASSENGER_PRESENT, b.readUnsignedInt()));
        register(946, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_PTO, b.readUnsignedInt()));
        register(947, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_FRONT_DIFF_LOCKED, b.readUnsignedInt()));
        register(948, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_REAR_DIFF_LOCKED, b.readUnsignedInt()));
        register(949, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_CENTRAL_DIFF_4HI_LOCKED, b.readUnsignedInt()));
        register(950, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_REAR_DIFF_4LO_LOCKED, b.readUnsignedInt()));
        register(951, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_TRAILER_AXLE1_LIFT, b.readUnsignedInt()));
        register(952, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_TRAILER_AXLE2_LIFT, b.readUnsignedInt()));
        register(1085, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_TRAILER_CONNECTED, b.readUnsignedInt()));
        register(1086, fmbXXX, (p, b) -> p.set(Position.KEY_CSF_START_STOP_INACTIVE, b.readUnsignedInt()));

        register(953, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_CHECK_ENGINE, b.readUnsignedInt()));
        register(954, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_ABS, b.readUnsignedInt()));
        register(955, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_ESP, b.readUnsignedInt()));
        register(956, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_ESP_TURNED_OFF, b.readUnsignedInt()));
        register(957, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_STOP, b.readUnsignedInt()));
        register(958, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_OIL_LEVEL, b.readUnsignedInt()));
        register(959, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_COOLANT_LEVEL, b.readUnsignedInt()));
        register(960, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_BATTERY_NOT_CHARGING, b.readUnsignedInt()));
        register(961, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_HANDBRAKE, b.readUnsignedInt()));
        register(962, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_AIRBAG, b.readUnsignedInt()));
        register(963, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_EPS, b.readUnsignedInt()));
        register(964, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_WARNING, b.readUnsignedInt()));
        register(965, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_LIGHTS_FAILURE, b.readUnsignedInt()));
        register(966, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_LOW_TIRE_PRESSURE, b.readUnsignedInt()));
        register(967, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_BRAKE_PADS_WEAR, b.readUnsignedInt()));
        register(968, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_LOW_FUEL, b.readUnsignedInt()));
        register(969, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_MAINTENANCE_REQUIRED, b.readUnsignedInt()));
        register(970, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_GLOW_PLUG, b.readUnsignedInt()));
        register(971, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_FAP, b.readUnsignedInt()));
        register(972, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_EPC, b.readUnsignedInt()));
        register(973, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_OIL_FILTER_CLOGGED, b.readUnsignedInt()));
        register(974, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_LOW_ENGINE_OIL_PRESSURE, b.readUnsignedInt()));
        register(975, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_ENGINE_OIL_HIGH_TEMP, b.readUnsignedInt()));
        register(976, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_LOW_COOLANT, b.readUnsignedInt()));
        register(977, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_HYDRAULIC_OIL_FILTER_CLOGGED, b.readUnsignedInt()));
        register(978, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_HYDRAULIC_LOW_PRESSURE, b.readUnsignedInt()));
        register(979, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_HYDRAULIC_OIL_LOW, b.readUnsignedInt()));
        register(980, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_HYDRAULIC_HIGH_TEMP, b.readUnsignedInt()));
        register(981, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_HYDRAULIC_OVERFLOW, b.readUnsignedInt()));
        register(982, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_AIR_FILTER_CLOGGED, b.readUnsignedInt()));
        register(983, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_FUEL_FILTER_CLOGGED, b.readUnsignedInt()));
        register(984, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_WATER_IN_FUEL, b.readUnsignedInt()));
        register(985, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_BRAKE_SYSTEM_FILTER_CLOGGED, b.readUnsignedInt()));
        register(986, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_LOW_WASHER_FLUID, b.readUnsignedInt()));
        register(987, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_LOW_ADBLUE, b.readUnsignedInt()));
        register(988, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_LOW_TRAILER_TIRE_PRESSURE, b.readUnsignedInt()));
        register(989, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_TRAILER_BRAKE_LINING_WEAR, b.readUnsignedInt()));
        register(990, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_TRAILER_BRAKE_HIGH_TEMP, b.readUnsignedInt()));
        register(991, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_TRAILER_PNEUMATIC_SUPPLY, b.readUnsignedInt()));
        register(992, fmbXXX, (p, b) -> p.set(Position.KEY_ISF_LOW_CNG, b.readUnsignedInt()));

        register(993, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_RIGHT_JOYSTICK_RIGHT, b.readUnsignedInt()));
        register(994, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_RIGHT_JOYSTICK_LEFT, b.readUnsignedInt()));
        register(995, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_RIGHT_JOYSTICK_FORWARD, b.readUnsignedInt()));
        register(996, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_RIGHT_JOYSTICK_BACK, b.readUnsignedInt()));
        register(997, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_LEFT_JOYSTICK_RIGHT, b.readUnsignedInt()));
        register(998, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_LEFT_JOYSTICK_LEFT, b.readUnsignedInt()));
        register(999, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_LEFT_JOYSTICK_FORWARD, b.readUnsignedInt()));
        register(1000, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_LEFT_JOYSTICK_BACK, b.readUnsignedInt()));

        register(1001, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_REAR_HYDRAULIC1, b.readUnsignedInt()));
        register(1002, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_REAR_HYDRAULIC2, b.readUnsignedInt()));
        register(1003, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_REAR_HYDRAULIC3, b.readUnsignedInt()));
        register(1004, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_REAR_HYDRAULIC4, b.readUnsignedInt()));
        register(1005, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_FRONT_HYDRAULIC1, b.readUnsignedInt()));
        register(1006, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_FRONT_HYDRAULIC2, b.readUnsignedInt()));
        register(1007, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_FRONT_HYDRAULIC3, b.readUnsignedInt()));
        register(1008, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_FRONT_HYDRAULIC4, b.readUnsignedInt()));

        register(1009, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_FRONT_HITCH, b.readUnsignedInt()));
        register(1010, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_REAR_HITCH, b.readUnsignedInt()));
        register(1011, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_FRONT_PTO, b.readUnsignedInt()));
        register(1012, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_REAR_PTO, b.readUnsignedInt()));
        register(1013, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_MOWING, b.readUnsignedInt()));
        register(1014, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_THRESHING, b.readUnsignedInt()));
        register(1015, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_GRAIN_RELEASE, b.readUnsignedInt()));
        register(1016, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_GRAIN_TANK_FULL_100, b.readUnsignedInt()));
        register(1017, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_GRAIN_TANK_FULL_70, b.readUnsignedInt()));
        register(1018, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_GRAIN_TANK_OPENED, b.readUnsignedInt()));
        register(1019, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_UNLOADER_DRIVE, b.readUnsignedInt()));
        register(1020, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_CLEANING_FAN_CTRL_OFF, b.readUnsignedInt()));
        register(1021, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_THRESHING_DRUM_CTRL_OFF, b.readUnsignedInt()));
        register(1022, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_STRAW_WALKER_CLOGGED, b.readUnsignedInt()));
        register(1023, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_THRESHING_DRUM_CLEARANCE, b.readUnsignedInt()));
        register(1024, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_HYDRAULICS_TEMP_LOW, b.readUnsignedInt()));
        register(1025, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_HYDRAULICS_TEMP_HIGH, b.readUnsignedInt()));
        register(1026, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_EAR_AUGER_SPEED_LOW, b.readUnsignedInt()));
        register(1027, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_GRAIN_AUGER_SPEED_LOW, b.readUnsignedInt()));
        register(1028, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_STRAW_CHOPPER_SPEED_LOW, b.readUnsignedInt()));
        register(1029, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_STRAW_SHAKER_SPEED_LOW, b.readUnsignedInt()));
        register(1030, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_FEEDER_SPEED_LOW, b.readUnsignedInt()));
        register(1031, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_STRAW_CHOPPER_ON, b.readUnsignedInt()));
        register(1032, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_CORN_HEADER_CONNECTED, b.readUnsignedInt()));
        register(1033, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_GRAIN_HEADER_CONNECTED, b.readUnsignedInt()));
        register(1034, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_FEEDER_REVERSE_ON, b.readUnsignedInt()));
        register(1035, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_HYDRAULIC_PUMP_FILTER_CLOGGED, b.readUnsignedInt()));
        register(1087, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_ADAPTER_PRESSURE_FILTER, b.readUnsignedInt()));
        register(1088, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_SERVICE2_REQUIRED, b.readUnsignedInt()));
        register(1089, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_DRAIN_FILTER_CLOGGED, b.readUnsignedInt()));
        // ASF Section Spraying
        register(1090, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_SECTION1_SPRAYING, b.readUnsignedByte()));
        register(1091, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_SECTION2_SPRAYING, b.readUnsignedByte()));
        register(1092, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_SECTION3_SPRAYING, b.readUnsignedByte()));
        register(1093, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_SECTION4_SPRAYING, b.readUnsignedByte()));
        register(1094, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_SECTION5_SPRAYING, b.readUnsignedByte()));
        register(1095, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_SECTION6_SPRAYING, b.readUnsignedByte()));
        register(1096, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_SECTION7_SPRAYING, b.readUnsignedByte()));
        register(1097, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_SECTION8_SPRAYING, b.readUnsignedByte()));
        register(1098, fmbXXX, (p, b) -> p.set(Position.KEY_ASF_SECTION9_SPRAYING, b.readUnsignedByte()));

        // USF Parameters
        register(1036, fmbXXX, (p, b) -> p.set(Position.KEY_USF_SPREADING, b.readUnsignedByte()));
        register(1037, fmbXXX, (p, b) -> p.set(Position.KEY_USF_POURING_CHEMICALS, b.readUnsignedByte()));
        register(1038, fmbXXX, (p, b) -> p.set(Position.KEY_USF_CONVEYOR_BELT, b.readUnsignedByte()));
        register(1039, fmbXXX, (p, b) -> p.set(Position.KEY_USF_SALT_SPREADER_DRIVE_WHEEL, b.readUnsignedByte()));
        register(1040, fmbXXX, (p, b) -> p.set(Position.KEY_USF_BRUSHES, b.readUnsignedByte()));
        register(1041, fmbXXX, (p, b) -> p.set(Position.KEY_USF_VACUUM_CLEANER, b.readUnsignedByte()));
        register(1042, fmbXXX, (p, b) -> p.set(Position.KEY_USF_WATER_SUPPLY, b.readUnsignedByte()));
        register(1043, fmbXXX, (p, b) -> p.set(Position.KEY_USF_SPREADING_ALT, b.readUnsignedByte()));
        register(1044, fmbXXX, (p, b) -> p.set(Position.KEY_USF_LIQUID_PUMP, b.readUnsignedByte()));
        register(1045, fmbXXX, (p, b) -> p.set(Position.KEY_USF_UNLOADING_HOPPER, b.readUnsignedByte()));
        register(1046, fmbXXX, (p, b) -> p.set(Position.KEY_USF_LOW_SALT_LEVEL, b.readUnsignedByte()));
        register(1047, fmbXXX, (p, b) -> p.set(Position.KEY_USF_LOW_WATER_LEVEL, b.readUnsignedByte()));
        register(1048, fmbXXX, (p, b) -> p.set(Position.KEY_USF_CHEMICALS, b.readUnsignedByte()));
        register(1049, fmbXXX, (p, b) -> p.set(Position.KEY_USF_COMPRESSOR, b.readUnsignedByte()));
        // USF Extra
        register(1050, fmbXXX, (p, b) -> p.set(Position.KEY_USF_WATER_VALVE_OPENED, b.readUnsignedByte()));
        register(1051, fmbXXX, (p, b) -> p.set(Position.KEY_USF_CABIN_MOVED_UP, b.readUnsignedByte()));
        register(1052, fmbXXX, (p, b) -> p.set(Position.KEY_USF_CABIN_MOVED_DOWN, b.readUnsignedByte()));
        register(1099, fmbXXX, (p, b) -> p.set(Position.KEY_USF_HYDRAULICS_NOT_PERMITTED, b.readUnsignedByte()));

        // CiSF Sections (1–8, each 3 params)
        register(1053, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION1_PRESENCE, b.readUnsignedByte()));
        register(1054, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION1_FILLED, b.readUnsignedByte()));
        register(1055, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION1_OVERFILLED, b.readUnsignedByte()));
        register(1056, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION2_PRESENCE, b.readUnsignedByte()));
        register(1057, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION2_FILLED, b.readUnsignedByte()));
        register(1058, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION2_OVERFILLED, b.readUnsignedByte()));
        register(1059, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION3_PRESENCE, b.readUnsignedByte()));
        register(1060, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION3_FILLED, b.readUnsignedByte()));
        register(1061, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION3_OVERFILLED, b.readUnsignedByte()));
        register(1062, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION4_PRESENCE, b.readUnsignedByte()));
        register(1063, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION4_FILLED, b.readUnsignedByte()));
        register(1064, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION4_OVERFILLED, b.readUnsignedByte()));
        register(1065, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION5_PRESENCE, b.readUnsignedByte()));
        register(1066, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION5_FILLED, b.readUnsignedByte()));
        register(1067, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION5_OVERFILLED, b.readUnsignedByte()));
        register(1068, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION6_PRESENCE, b.readUnsignedByte()));
        register(1069, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION6_FILLED, b.readUnsignedByte()));
        register(1070, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION6_OVERFILLED, b.readUnsignedByte()));
        register(1071, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION7_PRESENCE, b.readUnsignedByte()));
        register(1072, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION7_FILLED, b.readUnsignedByte()));
        register(1073, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION7_OVERFILLED, b.readUnsignedByte()));
        register(1074, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION8_PRESENCE, b.readUnsignedByte()));
        register(1075, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION8_FILLED, b.readUnsignedByte()));
        register(1076, fmbXXX, (p, b) -> p.set(Position.KEY_CISF_SECTION8_OVERFILLED, b.readUnsignedByte()));

        // Service & Vehicle
        register(400, fmbXXX, (p, b) -> p.set(Position.KEY_DISTANCE_TO_NEXT_SERVICE, b.readUnsignedInt()));
        register(450, fmbXXX, (p, b) -> p.set(Position.KEY_CNG_LEVEL_KG, b.readUnsignedShort()));
        register(859, fmbXXX, (p, b) -> p.set(Position.KEY_DISTANCE_FROM_NEED_SERVICE, b.readUnsignedInt()));
        register(860, fmbXXX, (p, b) -> p.set(Position.KEY_DISTANCE_FROM_LAST_SERVICE, b.readUnsignedInt()));
        register(861, fmbXXX, (p, b) -> p.set(Position.KEY_TIME_TO_NEXT_SERVICE, b.readUnsignedShort()));
        register(862, fmbXXX, (p, b) -> p.set(Position.KEY_TIME_FROM_NEED_SERVICE, b.readUnsignedShort()));
        register(863, fmbXXX, (p, b) -> p.set(Position.KEY_TIME_FROM_LAST_SERVICE, b.readUnsignedShort()));
        register(864, fmbXXX, (p, b) -> p.set(Position.KEY_DISTANCE_TO_NEXT_OIL_SERVICE, b.readUnsignedInt()));
        register(865, fmbXXX, (p, b) -> p.set(Position.KEY_TIME_TO_NEXT_OIL_SERVICE, b.readUnsignedShort()));
        register(866, fmbXXX, (p, b) -> p.set(Position.KEY_LVCAN_VEHICLE_RANGE, b.readUnsignedInt()));
        register(867, fmbXXX, (p, b) -> p.set(Position.KEY_LVCAN_TOTAL_CNG_COUNTED, b.readUnsignedInt()));

        // Bale Count
        register(1079, fmbXXX, (p, b) -> p.set(Position.KEY_TOTAL_BALE_COUNT, b.readUnsignedInt()));
        register(1080, fmbXXX, (p, b) -> p.set(Position.KEY_BALE_COUNT, b.readUnsignedInt()));
        register(1081, fmbXXX, (p, b) -> p.set(Position.KEY_CUT_BALE_COUNT, b.readUnsignedInt()));
        register(1082, fmbXXX, (p, b) -> p.set(Position.KEY_BALE_SLICES, b.readUnsignedInt()));

        // Road Speed
        register(1116, fmbXXX, (p, b) -> p.set(Position.KEY_LVCAN_MAX_ROAD_SPEED, b.readUnsignedByte()));
        register(1117, fmbXXX, (p, b) -> p.set(Position.KEY_LVCAN_EXCEEDED_ROAD_SPEED, b.readUnsignedByte()));

        // Road Sign Features
        register(1205, fmbXXX, (p, b) -> p.set(Position.KEY_RSF_SPEED_LIMIT_SIGN, b.readUnsignedByte()));
        register(1206, fmbXXX, (p, b) -> p.set(Position.KEY_RSF_END_SPEED_LIMIT_SIGN, b.readUnsignedByte()));
        register(1207, fmbXXX, (p, b) -> p.set(Position.KEY_RSF_SPEED_EXCEEDED, b.readUnsignedByte()));
        register(1208, fmbXXX, (p, b) -> p.set(Position.KEY_RSF_TIME_SPEED_LIMIT_SIGN, b.readUnsignedByte()));
        register(1209, fmbXXX, (p, b) -> p.set(Position.KEY_RSF_WEATHER_SPEED_LIMIT_SIGN, b.readUnsignedByte()));
    }

    private void decodeGh3000Parameter(Position position, int id, ByteBuf buf, int length) {
        switch (id) {
            case 1 -> position.set(Position.KEY_BATTERY_LEVEL, readValue(buf, length));
            case 2 -> position.set("usbConnected", readValue(buf, length) == 1);
            case 5 -> position.set("uptime", readValue(buf, length));
            case 20 -> position.set(Position.KEY_HDOP, readValue(buf, length) * 0.1);
            case 21 -> position.set(Position.KEY_VDOP, readValue(buf, length) * 0.1);
            case 22 -> position.set(Position.KEY_PDOP, readValue(buf, length) * 0.1);
            case 67 -> position.set(Position.KEY_BATTERY, readValue(buf, length) * 0.001);
            case 221 -> position.set("button", readValue(buf, length));
            case 222 -> {
                if (readValue(buf, length) == 1) {
                    position.addAlarm(Position.ALARM_SOS);
                }
            }
            case 240 -> position.set(Position.KEY_MOTION, readValue(buf, length) == 1);
            case 244 -> position.set(Position.KEY_ROAMING, readValue(buf, length) == 1);
            default -> position.set(Position.PREFIX_IO + id, readValue(buf, length));
        }
    }

    private void decodeParameter(Position position, int id, ByteBuf buf, int length, int codec, String model) {
        if (codec == CODEC_GH3000) {
            decodeGh3000Parameter(position, id, buf, length);
        } else {
            int index = buf.readerIndex();
            boolean decoded = false;
            for (var entry : PARAMETERS.getOrDefault(id, new HashMap<>()).entrySet()) {
                if (entry.getKey() == null || model != null && entry.getKey().contains(model)) {
                    entry.getValue().accept(position, buf);
                    decoded = true;
                    break;
                }
            }
            if (decoded) {
                buf.readerIndex(index + length);
            } else {
                position.set(Position.PREFIX_IO + id, readValue(buf, length));
            }
        }
    }

    private void decodeCell(
            Position position, Network network, String mncKey, String lacKey, String cidKey, String rssiKey) {
        if (position.hasAttribute(mncKey) && position.hasAttribute(lacKey) && position.hasAttribute(cidKey)) {
            CellTower cellTower = CellTower.from(
                    getConfig().getInteger(Keys.GEOLOCATION_MCC),
                    position.removeInteger(mncKey),
                    position.removeInteger(lacKey),
                    position.removeLong(cidKey));
            cellTower.setSignalStrength(position.removeInteger(rssiKey));
            network.addCellTower(cellTower);
        }
    }

    private void decodeNetwork(Position position, String model) {
        if ("TAT100".equals(model)) {
            Network network = new Network();
            decodeCell(position, network, "io1200", "io287", "io288", "io289");
            decodeCell(position, network, "io1201", "io290", "io291", "io292");
            decodeCell(position, network, "io1202", "io293", "io294", "io295");
            decodeCell(position, network, "io1203", "io296", "io297", "io298");
            if (network.getCellTowers() != null) {
                position.setNetwork(network);
            }
        } else {
            Integer cid2g = position.removeInteger("cid2g");
            Long cid4g = position.removeLong("cid4g");
            Integer lac = position.removeInteger("lac");
            if (lac != null && (cid2g != null || cid4g != null)) {
                Network network = new Network();
                CellTower cellTower;
                if (cid2g != null) {
                    cellTower = CellTower.fromLacCid(getConfig(), lac, cid2g);
                } else {
                    cellTower = CellTower.fromLacCid(getConfig(), lac, cid4g);
                    network.setRadioType("lte");
                }
                long operator = position.getInteger(Position.KEY_OPERATOR);
                if (operator >= 1000) {
                    cellTower.setOperator(operator);
                }
                network.addCellTower(cellTower);
                position.setNetwork(new Network(cellTower));
            }
        }
    }

    private int readExtByte(ByteBuf buf, int codec, int... codecs) {
        boolean ext = false;
        for (int c : codecs) {
            if (codec == c) {
                ext = true;
                break;
            }
        }
        if (ext) {
            return buf.readUnsignedShort();
        } else {
            return buf.readUnsignedByte();
        }
    }

    private void decodeLocation(Position position, ByteBuf buf, int codec, String model) {

        int globalMask = 0x0f;

        if (codec == CODEC_GH3000) {

            long time = buf.readUnsignedInt() & 0x3fffffff;
            time += 1167609600; // 2007-01-01 00:00:00

            globalMask = buf.readUnsignedByte();
            if (BitUtil.check(globalMask, 0)) {

                position.setTime(new Date(time * 1000));

                int locationMask = buf.readUnsignedByte();

                if (BitUtil.check(locationMask, 0)) {
                    position.setLatitude(buf.readFloat());
                    position.setLongitude(buf.readFloat());
                }

                if (BitUtil.check(locationMask, 1)) {
                    position.setAltitude(buf.readUnsignedShort());
                }

                if (BitUtil.check(locationMask, 2)) {
                    position.setCourse(buf.readUnsignedByte() * 360.0 / 256);
                }

                if (BitUtil.check(locationMask, 3)) {
                    position.setSpeed(UnitsConverter.knotsFromKph(buf.readUnsignedByte()));
                }

                if (BitUtil.check(locationMask, 4)) {
                    position.set(Position.KEY_SATELLITES, buf.readUnsignedByte());
                }

                if (BitUtil.check(locationMask, 5)) {
                    CellTower cellTower = CellTower.fromLacCid(
                            getConfig(), buf.readUnsignedShort(), buf.readUnsignedShort());

                    if (BitUtil.check(locationMask, 6)) {
                        cellTower.setSignalStrength((int) buf.readUnsignedByte());
                    }

                    if (BitUtil.check(locationMask, 7)) {
                        cellTower.setOperator(buf.readUnsignedInt());
                    }

                    position.setNetwork(new Network(cellTower));

                } else {
                    if (BitUtil.check(locationMask, 6)) {
                        position.set(Position.KEY_RSSI, buf.readUnsignedByte());
                    }
                    if (BitUtil.check(locationMask, 7)) {
                        position.set(Position.KEY_OPERATOR, buf.readUnsignedInt());
                    }
                }

            } else {

                getLastLocation(position, new Date(time * 1000));

            }

        } else {

            position.setTime(new Date(buf.readLong()));

            position.set("priority", buf.readUnsignedByte());

            position.setLongitude(buf.readInt() / 10000000.0);
            position.setLatitude(buf.readInt() / 10000000.0);
            position.setAltitude(buf.readShort());
            position.setCourse(buf.readUnsignedShort());

            int satellites = buf.readUnsignedByte();
            position.set(Position.KEY_SATELLITES, satellites);

            position.setValid(satellites != 0);

            position.setSpeed(UnitsConverter.knotsFromKph(buf.readUnsignedShort()));

            position.set(Position.KEY_EVENT, readExtByte(buf, codec, CODEC_8_EXT, CODEC_16));
            if (codec == CODEC_16) {
                buf.readUnsignedByte(); // generation type
            }

            readExtByte(buf, codec, CODEC_8_EXT); // total IO data records

        }

        // Read 1 byte data
        if (BitUtil.check(globalMask, 1)) {
            int cnt = readExtByte(buf, codec, CODEC_8_EXT);
            for (int j = 0; j < cnt; j++) {
                decodeParameter(position, readExtByte(buf, codec, CODEC_8_EXT, CODEC_16), buf, 1, codec, model);
            }
        }

        // Read 2 byte data
        if (BitUtil.check(globalMask, 2)) {
            int cnt = readExtByte(buf, codec, CODEC_8_EXT);
            for (int j = 0; j < cnt; j++) {
                decodeParameter(position, readExtByte(buf, codec, CODEC_8_EXT, CODEC_16), buf, 2, codec, model);
            }
        }

        // Read 4 byte data
        if (BitUtil.check(globalMask, 3)) {
            int cnt = readExtByte(buf, codec, CODEC_8_EXT);
            for (int j = 0; j < cnt; j++) {
                decodeParameter(position, readExtByte(buf, codec, CODEC_8_EXT, CODEC_16), buf, 4, codec, model);
            }
        }

        // Read 8 byte data
        if (codec == CODEC_8 || codec == CODEC_8_EXT || codec == CODEC_16) {
            int cnt = readExtByte(buf, codec, CODEC_8_EXT);
            for (int j = 0; j < cnt; j++) {
                decodeParameter(position, readExtByte(buf, codec, CODEC_8_EXT, CODEC_16), buf, 8, codec, model);
            }
        }

        // Read 16 byte data
        if (extended) {
            int cnt = readExtByte(buf, codec, CODEC_8_EXT);
            for (int j = 0; j < cnt; j++) {
                int id = readExtByte(buf, codec, CODEC_8_EXT, CODEC_16);
                position.set(Position.PREFIX_IO + id, ByteBufUtil.hexDump(buf.readSlice(16)));
            }
        }

        // Read X byte data
        if (codec == CODEC_8_EXT) {
            int cnt = buf.readUnsignedShort();
            for (int j = 0; j < cnt; j++) {
                int id = buf.readUnsignedShort();
                int length = buf.readUnsignedShort();
                if (id == 256 || id == 325) {
                    position.set(Position.KEY_VIN,
                            buf.readSlice(length).toString(StandardCharsets.US_ASCII));
                } else if (id == 281) {
                    position.set(Position.KEY_DTCS,
                            buf.readSlice(length).toString(StandardCharsets.US_ASCII).replace(',', ' '));
                } else if (id == 385) {
                    ByteBuf data = buf.readSlice(length);
                    data.readUnsignedByte(); // data part
                    int index = 1;
                    while (data.isReadable()) {
                        int flags = data.readUnsignedByte();
                        if (BitUtil.from(flags, 4) > 0) {
                            position.set("beacon" + index + "Uuid", ByteBufUtil.hexDump(data.readSlice(16)));
                            position.set("beacon" + index + "Major", data.readUnsignedShort());
                            position.set("beacon" + index + "Minor", data.readUnsignedShort());
                        } else {
                            position.set("beacon" + index + "Namespace", ByteBufUtil.hexDump(data.readSlice(10)));
                            position.set("beacon" + index + "Instance", ByteBufUtil.hexDump(data.readSlice(6)));
                        }
                        position.set("beacon" + index + "Rssi", (int) data.readByte());
                        if (BitUtil.check(flags, 1)) {
                            position.set("beacon" + index + "Battery", data.readUnsignedShort() * 0.01);
                        }
                        if (BitUtil.check(flags, 2)) {
                            position.set("beacon" + index + "Temp", data.readUnsignedShort());
                        }
                        index += 1;
                    }
                } else if (id == 548 || id == 10828 || id == 10829 || id == 10831 || id == 11317) {
                    ByteBuf data = buf.readSlice(length);
                    data.readUnsignedByte(); // header
                    for (int i = 1; data.isReadable(); i++) {
                        ByteBuf beacon = data.readSlice(data.readUnsignedByte());
                        while (beacon.isReadable()) {
                            int parameterId = beacon.readUnsignedByte();
                            int parameterLength = beacon.readUnsignedByte();
                            switch (parameterId) {
                                case 0 -> position.set("tag" + i + "Rssi", (int) beacon.readByte());
                                case 1 -> {
                                    String beaconId = ByteBufUtil.hexDump(beacon.readSlice(parameterLength));
                                    position.set("tag" + i + "Id", beaconId);
                                }
                                case 2 -> {
                                    ByteBuf beaconData = beacon.readSlice(parameterLength);
                                    int flag = beaconData.readUnsignedByte();
                                    if (BitUtil.check(flag, 6)) {
                                        position.set("tag" + i + "LowBattery", true);
                                    }
                                    if (BitUtil.check(flag, 7)) {
                                        position.set("tag" + i + "Voltage", beaconData.readUnsignedByte() * 10 + 2000);
                                    }
                                }
                                case 5 -> {
                                    String name = beacon.readCharSequence(
                                            parameterLength, StandardCharsets.UTF_8).toString();
                                    position.set("tag" + i + "Name", name);
                                }
                                case 6 -> position.set("tag" + i + "Temp", beacon.readShort());
                                case 7 -> position.set("tag" + i + "Humidity", beacon.readUnsignedByte());
                                case 8 -> position.set("tag" + i + "Magnet", beacon.readUnsignedByte() > 0);
                                case 9 -> position.set("tag" + i + "Motion", beacon.readUnsignedByte() > 0);
                                case 10 -> position.set("tag" + i + "MotionCount", beacon.readUnsignedShort());
                                case 11 -> position.set("tag" + i + "Pitch", (int) beacon.readByte());
                                case 12 -> position.set("tag" + i + "AngleRoll", (int) beacon.readShort());
                                case 13 -> position.set("tag" + i + "LowBattery", beacon.readUnsignedByte());
                                case 14 -> position.set("tag" + i + "Battery", beacon.readUnsignedShort());
                                case 15 -> position.set("tag" + i + "Mac", ByteBufUtil.hexDump(beacon.readSlice(6)));
                                default -> beacon.skipBytes(parameterLength);
                            }
                        }
                    }
                } else {
                    position.set(Position.PREFIX_IO + id, ByteBufUtil.hexDump(buf.readSlice(length)));
                }
            }
        }

        decodeNetwork(position, model);

        if (model != null && model.matches("FM.6..")) {
            Long driverMsb = (Long) position.getAttributes().get("io195");
            Long driverLsb = (Long) position.getAttributes().get("io196");
            if (driverMsb != null && driverLsb != null) {
                String driver = new String(ByteBuffer.allocate(16).putLong(driverMsb).putLong(driverLsb).array());
                position.set(Position.KEY_DRIVER_UNIQUE_ID, driver);
            }
        }
    }

    private List<Position> parseData(
            Channel channel, SocketAddress remoteAddress, ByteBuf buf, int locationPacketId, String... imei) {
        List<Position> positions = new LinkedList<>();

        if (!connectionless) {
            buf.readUnsignedInt(); // data length
        }

        int codec = buf.readUnsignedByte();
        int count = buf.readUnsignedByte();

        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, imei);
        if (deviceSession == null) {
            return null;
        }

        for (int i = 0; i < count; i++) {
            Position position = new Position(getProtocolName());

            position.setDeviceId(deviceSession.getDeviceId());
            position.setValid(true);

            if (codec == CODEC_13) {
                buf.readUnsignedByte(); // type
                int length = buf.readInt() - 4;
                getLastLocation(position, new Date(buf.readUnsignedInt() * 1000));
                if (BufferUtil.isPrintable(buf, length)) {
                    String data = buf.readCharSequence(length, StandardCharsets.US_ASCII).toString().trim();
                    if (data.startsWith("GTSL")) {
                        position.set(Position.KEY_DRIVER_UNIQUE_ID, data.split("\\|")[4]);
                    } else {
                        position.set(Position.KEY_RESULT, data);
                    }
                } else {
                    position.set(Position.KEY_RESULT,
                            ByteBufUtil.hexDump(buf.readSlice(length)));
                }
            } else if (codec == CODEC_12) {
                decodeSerial(channel, remoteAddress, deviceSession, position, buf);
            } else {
                decodeLocation(position, buf, codec, getDeviceModel(deviceSession));
            }

            if (!position.getOutdated() || !position.getAttributes().isEmpty()) {
                positions.add(position);
            }
        }

        if (channel != null && codec != CODEC_12 && codec != CODEC_13) {
            ByteBuf response = Unpooled.buffer();
            if (connectionless) {
                response.writeShort(5);
                response.writeShort(0);
                response.writeByte(0x01);
                response.writeByte(locationPacketId);
                response.writeByte(count);
            } else {
                response.writeInt(count);
            }
            channel.writeAndFlush(new NetworkMessage(response, remoteAddress));
        }

        return positions.isEmpty() ? null : positions;
    }

    @Override
    protected Object decode(Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        ByteBuf buf = (ByteBuf) msg;

        if (connectionless) {
            return decodeUdp(channel, remoteAddress, buf);
        } else {
            return decodeTcp(channel, remoteAddress, buf);
        }
    }

    private Object decodeTcp(Channel channel, SocketAddress remoteAddress, ByteBuf buf) {

        if (buf.readableBytes() == 1 && buf.readUnsignedByte() == 0xff) {
            return null;
        } else if (buf.getUnsignedShort(0) > 0) {
            parseIdentification(channel, remoteAddress, buf);
        } else {
            buf.skipBytes(4);
            return parseData(channel, remoteAddress, buf, 0);
        }

        return null;
    }

    private Object decodeUdp(Channel channel, SocketAddress remoteAddress, ByteBuf buf) {

        buf.readUnsignedShort(); // length
        buf.readUnsignedShort(); // packet id
        buf.readUnsignedByte(); // packet type
        int locationPacketId = buf.readUnsignedByte();
        String imei = buf.readSlice(buf.readUnsignedShort()).toString(StandardCharsets.US_ASCII);

        return parseData(channel, remoteAddress, buf, locationPacketId, imei);

    }

}
