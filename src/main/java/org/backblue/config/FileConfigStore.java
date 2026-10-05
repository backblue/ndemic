package org.backblue.config;

import org.backblue.utilities.Util;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

// Stores each document as data/<fileName>. Changes rewrite the whole file atomically.
public final class FileConfigStore implements ConfigStore {

    @Override
    public Optional<JSONObject> read(Config file) throws ReadException {
        Path path = path(file);
        try {
            return Optional.of(new JSONObject(Files.readString(path)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (SecurityException e) {
            throw new ReadException("Permission denied to read resource. Delete " + path + " to have it regenerated", e);
        } catch (Exception e) {
            throw new ReadException("Cannot read local file. Delete " + path + " to have it regenerated", e);
        }
    }

    @Override
    public void save(Change change) throws Exception {
        Util.writeAtomically(path(change.file()), change.document().toString(4));
    }

    @Override
    public String describe(Config file) {
        return path(file).toString();
    }

    private static Path path(Config file) {
        return Path.of("data", file.fileName());
    }
}
