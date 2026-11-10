package com.surf.tracker.core.infrastructure.migration;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** H2 counterpart of the V8 PostgreSQL trigger. Keep compatible with the applied migration. */
public final class DeviceIdentityTriggerV8 implements Trigger {
    private String ownerKind;
    private String registry;
    private int ownerIndex;
    private int deviceIndex;

    @Override
    public void init(Connection connection, String schemaName, String triggerName,
                     String tableName, boolean before, int type) throws SQLException {
        String ownerColumn;
        if ("vehicles".equalsIgnoreCase(tableName)) {
            ownerKind = "VEHICLE";
            ownerColumn = "vehicle_id";
        } else if ("trackers".equalsIgnoreCase(tableName)) {
            ownerKind = "TRACKER";
            ownerColumn = "tracker_id";
        } else {
            throw new SQLException("Unsupported device owner table", "42000");
        }
        // Resolve ordinals from the actual migrated schema rather than freezing column positions.
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("select * from " + quoted(schemaName) + "." + quoted(tableName) + " where 1=0")) {
            var metadata = rows.getMetaData();
            ownerIndex = -1;
            deviceIndex = -1;
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                if (ownerColumn.equalsIgnoreCase(metadata.getColumnName(column))) ownerIndex = column - 1;
                if ("device_id".equalsIgnoreCase(metadata.getColumnName(column))) deviceIndex = column - 1;
            }
        }
        if (ownerIndex < 0 || deviceIndex < 0) throw new SQLException("Missing device identity columns", "42000");
        registry = quoted(schemaName) + ".device_identity";
    }

    @Override
    public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        if (oldRow != null && newRow != null
                && Objects.equals(oldRow[deviceIndex], newRow[deviceIndex])
                && Objects.equals(oldRow[ownerIndex], newRow[ownerIndex])) return;

        if (oldRow != null && oldRow[deviceIndex] != null) {
            try (var statement = connection.prepareStatement("delete from " + registry
                    + " where device_id=? and owner_kind=? and owner_id=?")) {
                statement.setObject(1, oldRow[deviceIndex]);
                statement.setString(2, ownerKind);
                statement.setObject(3, oldRow[ownerIndex]);
                statement.executeUpdate();
            }
        }
        if (newRow != null && newRow[deviceIndex] != null) {
            try (var statement = connection.prepareStatement("insert into " + registry
                    + " (device_id, owner_kind, owner_id) values (?,?,?)")) {
                statement.setObject(1, newRow[deviceIndex]);
                statement.setString(2, ownerKind);
                statement.setObject(3, newRow[ownerIndex]);
                statement.executeUpdate();
            }
        }
    }

    private static String quoted(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
