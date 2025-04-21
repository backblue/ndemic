package org.backblue.libraries;

import org.backblue.Core;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.Iterator;
import java.util.Objects;

public class SQLJSON extends Core {

    private final String userId;
    private final JSONObject json;

    @Override
    public String toString() {return json.toString();}

    public SQLJSON writeString(String key, String value) {
        if (json.has(key)) {
            json.remove(key);
        }
        json.put(key, value);
        return this;
    }

    public SQLJSON writeInt(String key, int value) {
        if (json.has(key)) {
            json.remove(key);
        }
        json.put(key, value);
        return this;
    }

    public SQLJSON writeLong(String key, Long value) {
        if (json.has(key)) {
            json.remove(key);
        }
        json.put(key, value);
        return this;
    }

    public SQLJSON writeJSONObject(String key, JSONObject value) throws UnsupportedOperationException {
        if (Core.SETTINGS.get("useSQL").equals(true)) {
            throw new UnsupportedOperationException("Cannot write Object directly to SQL");
        } else if (json.has(key)) {
            json.remove(key);
        }
        json.put(key, value);
        return this;
    }

    public String readString(String key) {
        try {
            return json.getString(key);
        } catch (JSONException e) {
            return null;
        }
    }

    public Integer readInt(String key) {
        try {
            return json.getInt(key);
        } catch (JSONException e) {
            return null;
        }
    }

    public Long readLong(String key) {
        try {
            return json.getLong(key);
        } catch (JSONException e) {
            return null;
        }
    }

    public JSONObject readJSONObject(String key) {
        try {
            return json.getJSONObject(key);
        } catch (JSONException e) {
            return null;
        }
    }

    public static SQLJSON read(String userId, String table) {
        return new SQLJSON(userId, table);
    }

    public void write(String table) {

        json.remove("lastRefresh");
        json.remove("cacheUsername");
        json.put("lastRefresh", Instant.now().getEpochSecond());
        json.put("cacheUsername", Objects.requireNonNull(Core.BOT.getUserById(userId)).getName());

        if (Core.SETTINGS.get("useSQL").equals(false)) {
            try {
                Files.writeString(Path.of("data/users/" + userId + ".json"), json.toString());
            } catch (IOException e) {
                System.out.println(Instant.now() + " - Error writing user file: " + e.getMessage());
            }
        } else {
            if (exists(userId, table)) {
                update(table);
            } else {
                insert(table);
            }
        }
    }

    private SQLJSON(String userId, String table) {
        this.userId = userId;
        this.json = get(userId, table);
    }

    private static JSONObject get(String userId, String table) {
        if (Core.SETTINGS.get("useSQL").equals(true)) {
            return sqlGet(userId, table);
        } else {
            try {
                return new JSONObject(Files.readString(Path.of("data/users/" + userId)));
            } catch (Exception e) {
                return new JSONObject().put("id", userId).put("lastRefresh", Instant.now().getEpochSecond()).put("cacheUsername", Core.BOT.getUserById(userId).getName());
            }
        }
    }

    private static JSONObject sqlGet(String userId, String table) {
        if (exists(userId, table)) {
            JSONObject json = new JSONObject();
            String query = "SELECT * FROM " + table + " WHERE id = ?";
            try (Connection conn = openConnection()) {
                PreparedStatement statement = conn.prepareStatement(query);
                statement.setString(1, userId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int columnCount = meta.getColumnCount();

                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = meta.getColumnLabel(i);
                            var value = rs.getObject(i);

                            // Handle NULLs explicitly
                            json.put(columnName, value != null ? value : JSONObject.NULL);
                        }
                        return json;
                    } else {
                        return new JSONObject().put("id", userId).put("lastRefresh", Instant.now().getEpochSecond()).put("cacheUsername", Objects.requireNonNull(Core.BOT.getUserById(userId)).getName());
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return new JSONObject().put("id", userId).put("lastRefresh", Instant.now().getEpochSecond()).put("cacheUsername", Objects.requireNonNull(Core.BOT.getUserById(userId)).getName());
        }
    }

    private void update(String table) {


        long id = Long.parseLong(userId);
        json.remove("id");

        StringBuilder setClause = new StringBuilder();
        int fieldCount = 0;

        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (fieldCount > 0) {
                setClause.append(", ");
            }
            setClause.append(key).append(" = ?");
            fieldCount++;
        }

        String query = "UPDATE " + table + " SET " + setClause + " WHERE id = ?";

        try (Connection conn = openConnection()) {
            PreparedStatement stmt = conn.prepareStatement(query);
            int index = 1;

            for (String key : json.keySet()) {
                if (json.get(key) == JSONObject.NULL) {
                    stmt.setObject(index++, null);
                } else {
                    stmt.setObject(index++, json.get(key));
                }
            }

            stmt.setObject(index, id); // Add id at the end
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }

        json.put("id", id);
    }
    public void insert(String table) {

        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        int fieldCount = 0;

        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (fieldCount > 0) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(key);
            placeholders.append("?");
            fieldCount++;
        }

        String query = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")";


        try (Connection conn = openConnection()) {
            PreparedStatement stmt = conn.prepareStatement(query);
            int index = 1;
            for (String key : json.keySet()) {
                Object value = json.get(key);
                if (value == JSONObject.NULL) {
                    stmt.setObject(index++, null);
                } else {
                    stmt.setObject(index++, value);
                }
            }

            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    private static boolean exists(String userId, String table) {
        String query = "SELECT 1 FROM " + table + " WHERE id = ? LIMIT 1";
        try (Connection conn = openConnection()) {
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                boolean exists = rs.next();
                return exists; // true if row exists
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private static Connection openConnection() {
        if (Core.SETTINGS.get("useSQL").equals(true)) {
            try {
                return DriverManager.getConnection(Core.SECURE_KEYS.getProperty("JDBC"));
            } catch (SQLException e) {
                System.out.println("Failed to connect to server. Turn off 'useSQL' in settings.json or check your JDBC URL.\n" + e);
                System.exit(1);
            }
        }
        return null;
    }
}
