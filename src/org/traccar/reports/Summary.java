/*
 * Copyright 2016 Anton Tananaev (anton.tananaev@gmail.com)
 * Copyright 2016 Andrey Kunitsyn (abyss@fox5.ru)
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
package org.traccar.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;

import javax.json.Json;
import javax.json.JsonArrayBuilder;

import org.traccar.Context;
import org.traccar.model.Position;
import org.traccar.reports.model.SummaryReport;
import org.traccar.web.CsvBuilder;
import org.traccar.web.JsonConverter;

public final class Summary {

    private Summary() {
    }

    private static SummaryReport calculateSummaryResult(long deviceId, Date from, Date to) throws SQLException {
        SummaryReport result = new SummaryReport();
        result.setDeviceId(deviceId);
        result.setDeviceName(Context.getDeviceManager().getDeviceById(deviceId).getName());
        Collection<Position> positions = Context.getDataManager().getPositions(deviceId, from, to);
        if (positions != null && !positions.isEmpty()) {
            Position firstPosition = null;
            Position previousPosition = null;
            double speedSum = 0;
            for (Position position : positions) {
                if (firstPosition == null) {
                    firstPosition = position;
                }
                if (previousPosition != null
                        && position.getAttributes().get(Position.KEY_IGNITION) != null
                        && Boolean.parseBoolean(position.getAttributes().get(Position.KEY_IGNITION).toString())
                        && previousPosition.getAttributes().get(Position.KEY_IGNITION) != null
                        && Boolean.parseBoolean(previousPosition.getAttributes()
                                .get(Position.KEY_IGNITION).toString())) {
                    result.addEngineHours(position.getFixTime().getTime()
                            - previousPosition.getFixTime().getTime());
                }
                previousPosition = position;
                speedSum += position.getSpeed();
                result.setMaxSpeed(position.getSpeed());
            }
            if (firstPosition.getAttributes().containsKey(Position.KEY_ODOMETER)
                    && previousPosition.getAttributes().containsKey(Position.KEY_ODOMETER)) {
                result.setDistance((Integer.parseInt(previousPosition.getAttributes().get(Position.KEY_ODOMETER)
                        .toString())
                        - Integer.parseInt(firstPosition.getAttributes().get(Position.KEY_ODOMETER).toString()))
                        * 1000);
            } else if (firstPosition.getAttributes().containsKey(Position.KEY_TOTAL_DISTANCE)
                    && previousPosition.getAttributes().containsKey(Position.KEY_TOTAL_DISTANCE)) {
                result.setDistance(((Number) previousPosition.getAttributes().get(Position.KEY_TOTAL_DISTANCE))
                        .doubleValue()
                        - ((Number) firstPosition.getAttributes().get(Position.KEY_TOTAL_DISTANCE)).doubleValue());
            }
            result.setDistance(new BigDecimal(result.getDistance())
                    .setScale(2, RoundingMode.HALF_EVEN).doubleValue());
            result.setAverageSpeed(new BigDecimal(speedSum / positions.size())
                    .setScale(3, RoundingMode.HALF_EVEN).doubleValue());
        }
        return result;
    }

    public static String getJson(long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws SQLException {
        JsonArrayBuilder json = Json.createArrayBuilder();
        for (long deviceId: ReportUtils.getDeviceList(deviceIds, groupIds)) {
            Context.getPermissionsManager().checkDevice(userId, deviceId);
            json.add(JsonConverter.objectToJson(calculateSummaryResult(deviceId, from, to)));
        }
        return json.build().toString();
    }

    public static String getCsv(long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to) throws SQLException {
        CsvBuilder csv = new CsvBuilder();
        csv.addHeaderLine(new SummaryReport());
        for (long deviceId: ReportUtils.getDeviceList(deviceIds, groupIds)) {
            Context.getPermissionsManager().checkDevice(userId, deviceId);
            csv.addLine(calculateSummaryResult(deviceId, from, to));
        }
        return csv.build();
    }
}
