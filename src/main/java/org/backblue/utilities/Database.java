package org.backblue.utilities;

import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

// Connection source for the PostgreSQL server. Each caller opens and closes its own connection.
public final class Database {

    private static final Logger Log = LoggerFactory.getLogger(Database.class);

    private static volatile DataSource dataSource;

    private Database() {
        throw new UnsupportedOperationException("Database cannot be instantiated");
    }

    // Connects, then applies db/schema.sql. Throws IllegalStateException if the database cannot be used.
    public static synchronized void init(Properties props) {
        if (dataSource != null) throw new IllegalStateException("Database already initialized");

        String link = props.getProperty("SQL_LINK", "").trim();
        if (link.isEmpty()) throw new IllegalStateException("bot.properties is missing: SQL_LINK");

        PGSimpleDataSource ds = new PGSimpleDataSource();
        String[] urlCredentials = applyLink(ds, link);
        String user = props.getProperty("SQL_USER", "").trim();
        String password = props.getProperty("SQL_PASS", "");
        ds.setUser(user.isEmpty() ? urlCredentials[0] : user);
        ds.setPassword(password.isEmpty() ? urlCredentials[1] : password);
        if (ds.getUser() == null || ds.getUser().isEmpty()) {
            throw new IllegalStateException("bot.properties is missing: SQL_USER");
        }
        ds.setConnectTimeout(10);
        ds.setApplicationName("ndemic");

        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute(Util.readResource("db/schema.sql"));
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot connect to or initialize database: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load internal file db/schema.sql", e);
        }

        dataSource = ds;
        Log.info("Connected to database {}", ds.getDatabaseName());
    }

    public static Connection connection() throws SQLException {
        DataSource ds = dataSource;
        if (ds == null) throw new IllegalStateException("Database not initialized");
        return ds.getConnection();
    }

    // Accepts a JDBC URL or a libpq URI (postgresql://user:pass@host:port/db?params).
    // JDBC rejects credentials inside the URL, so they are returned as {user, password} instead.
    private static String[] applyLink(PGSimpleDataSource ds, String link) {
        if (link.startsWith("jdbc:")) {
            ds.setUrl(link);
            return new String[]{null, null};
        }
        if (!link.startsWith("postgresql://") && !link.startsWith("postgres://")) {
            throw new IllegalStateException("SQL_LINK must start with jdbc:postgresql://, postgresql:// or postgres://");
        }

        URI uri;
        try {
            uri = new URI(link);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("SQL_LINK is not a valid URI");
        }
        if (uri.getHost() == null) throw new IllegalStateException("SQL_LINK has no host");

        StringBuilder url = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() != -1) url.append(':').append(uri.getPort());
        url.append(uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath());
        if (uri.getRawQuery() != null) url.append('?').append(uri.getRawQuery());
        ds.setUrl(url.toString());

        String userInfo = uri.getUserInfo();
        if (userInfo == null) return new String[]{null, null};
        int colon = userInfo.indexOf(':');
        return colon < 0
                ? new String[]{userInfo, null}
                : new String[]{userInfo.substring(0, colon), userInfo.substring(colon + 1)};
    }
}
