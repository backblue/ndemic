package org.backblue.utilities;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public record Database(@NotNull String url, @NotNull String username, @NotNull String password) {

    public Database {
        if (url.isBlank()) throw new IllegalArgumentException();
        if (username.isBlank()) throw new IllegalArgumentException();
        if (password.isBlank()) throw new IllegalArgumentException();
    }

    public Connection getConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("connectTimeout", "10");
        props.setProperty("socketTimeout", "30");
        return DriverManager.getConnection(url, props);
    }

    @Override
    public @NotNull String toString() {
        return this.getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    }
}
