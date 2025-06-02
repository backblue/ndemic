package org.backblue.utilities;

import org.backblue.Bot;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.Iterator;
import java.util.Objects;

public class SQLProfile {

    private final String userId;
    private final JSONObject json;

    @Override
    public String toString() {
        return json.toString();
    }

    public SQLProfile writeString(String key, String value) {
        if (json.has(key)) {
            json.remove(key);
        }
        json.put(key, value);
        return this;
    }

    public SQLProfile writeInt(String key, int value) {
        if (json.has(key)) {
            json.remove(key);
        }
        json.put(key, value);
        return this;
    }

    public SQLProfile writeLong(String key, Long value) {
        if (json.has(key)) {
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

    public static SQLProfile read(String userId, String table) {
        return new SQLProfile(userId, table);
    }

    public static JSONArray readAllIntoArray(String table) {
        JSONArray array = new JSONArray();
        String query = "SELECT * FROM " + table;
        try (Connection conn = openConnection();
             PreparedStatement statement = conn.prepareStatement(query);
             ResultSet rs = statement.executeQuery()) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            while (rs.next()) {
                JSONObject rowJson = new JSONObject();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = meta.getColumnLabel(i);
                    Object value = rs.getObject(i);
                    rowJson.put(columnName, value != null ? value : JSONObject.NULL);
                }
                array.put(rowJson);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null; // or return empty JSONArray
        }

        return array;
    }

    public void write(String table) {

        json.remove("lastRefresh");
        json.remove("cacheUsername");
        json.put("lastRefresh", Instant.now().getEpochSecond());
        json.put("cacheUsername", Objects.requireNonNull(Bot.getBot().getJDA().getUserById(userId)).getName());

        if (exists(userId, table)) {
            update(table);
        } else {
            insert(table);
        }

    }

    private SQLProfile(String userId, String table) {
        this.userId = userId;
        this.json = get(userId, table);
    }

    private static JSONObject get(String userId, String table) {
        return sqlGet(userId, table);
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
                        return new JSONObject().put("id", userId).put("lastRefresh", Instant.now().getEpochSecond()).put("cacheUsername", Objects.requireNonNull(Bot.getBot().getJDA().getUserById(userId)).getName());
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return new JSONObject().put("id", userId).put("lastRefresh", Instant.now().getEpochSecond()).put("cacheUsername", Objects.requireNonNull(Bot.getBot().getJDA().getUserById(userId)).getName());
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

    public static boolean exists(String userId, String table) {
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

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(Bot.getBot().getSQL());
    }
}