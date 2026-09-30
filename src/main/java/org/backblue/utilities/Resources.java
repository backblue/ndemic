package org.backblue.utilities;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public final class Resources {

    private Resources() {}

    public static String readString(String path) throws IOException {
        try (InputStream stream = Resources.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new FileNotFoundException(path);
            return new String(stream.readAllBytes());
        }
    }
}
