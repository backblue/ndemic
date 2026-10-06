package org.backblue.config;

import org.backblue.utilities.Database;
import org.json.JSONObject;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

// Stores each document as one jsonb row in the config table (see db/schema.sql).
// Every change runs in a transaction together with its config_history row.
public final class PgConfigStore implements ConfigStore {

    @Override
    public Optional<JSONObject> read(Config file) throws ReadException {
        try (Connection c = Database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT data::text FROM config WHERE name = ?")) {
            ps.setString(1, file.key());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new JSONObject(rs.getString(1))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ReadException("Cannot read " + describe(file) + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void save(Change change) throws Exception {
        String name = change.file().key();
        try (Connection c = Database.connection()) {
            c.setAutoCommit(false);
            try {
                Array path = c.createArrayOf("text", change.tokens().toArray());

                // Locks the row until commit and captures the stored value for history.
                boolean exists;
                String oldValue = null;
                try (PreparedStatement ps = c.prepareStatement("SELECT data #> ?::text[] FROM config WHERE name = ? FOR UPDATE")) {
                    ps.setArray(1, path);
                    ps.setString(2, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        exists = rs.next();
                        if (exists && change.op() != Op.APPEND) oldValue = rs.getString(1);
                    }
                }

                if (!exists || change.tokens().isEmpty()) {
                    upsert(c, name, change.document());
                } else {
                    update(c, name, path, change);
                }
                history(c, name, change, oldValue);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Override
    public String describe(Config file) {
        return "database row config '" + file.key() + "'";
    }

    private static void upsert(Connection c, String name, JSONObject document) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO config (name, data) VALUES (?, ?::jsonb)
                ON CONFLICT (name) DO UPDATE
                    SET data = EXCLUDED.data, version = config.version + 1, updated_at = now()""")) {
            ps.setString(1, name);
            ps.setString(2, document.toString());
            ps.executeUpdate();
        }
    }

    private static void update(Connection c, String name, Array path, Change change) throws SQLException {
        String data = switch (change.op()) {
            case SET -> "jsonb_set(data, ?::text[], ?::jsonb, true)";
            case REMOVE -> "data #- ?::text[]";
            case APPEND -> "jsonb_set(data, ?::text[], (data #> ?::text[]) || jsonb_build_array(?::jsonb))";
        };
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE config SET data = " + data + ", version = version + 1, updated_at = now() WHERE name = ?")) {
            int i = 1;
            ps.setArray(i++, path);
            if (change.op() == Op.APPEND) ps.setArray(i++, path);
            if (change.op() != Op.REMOVE) ps.setString(i++, JSONObject.valueToString(change.newValue()));
            ps.setString(i, name);
            ps.executeUpdate();
        }
    }

    private static void history(Connection c, String name, Change change, String oldValue) throws SQLException {
        String path = change.tokens().isEmpty() ? null : change.path() + (change.op() == Op.APPEND ? "/-" : "");
        String newValue = change.op() == Op.REMOVE ? null : JSONObject.valueToString(change.newValue());
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO config_history (name, path, old_value, new_value, changed_by)
                VALUES (?, ?, ?::jsonb, ?::jsonb, ?)""")) {
            ps.setString(1, name);
            ps.setString(2, path);
            ps.setString(3, oldValue);
            ps.setString(4, newValue);
            ps.setString(5, change.changedBy());
            ps.executeUpdate();
        }
    }
}
