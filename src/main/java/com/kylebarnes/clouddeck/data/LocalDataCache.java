package com.kylebarnes.clouddeck.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

public class LocalDataCache {
    private final Path cacheRoot;

    public LocalDataCache() {
        this(Path.of(System.getProperty("user.home"), ".clouddeck", "cache"));
    }

    LocalDataCache(Path cacheRoot) {
        this.cacheRoot = cacheRoot;
    }

    public String getText(String namespace, String cacheKey, Duration maxAge, CacheFetcher fetcher) throws Exception {
        Path cachePath = cachePath(namespace, cacheKey);
        if (isFresh(cachePath, maxAge)) {
            return Files.readString(cachePath, StandardCharsets.UTF_8);
        }

        try {
            String freshText = fetcher.fetch();
            writeCache(cachePath, freshText == null ? "" : freshText);
            return freshText == null ? "" : freshText;
        } catch (Exception exception) {
            if (Files.exists(cachePath)) {
                return Files.readString(cachePath, StandardCharsets.UTF_8);
            }
            throw exception;
        }
    }

    private boolean isFresh(Path cachePath, Duration maxAge) {
        if (maxAge == null || !Files.exists(cachePath)) {
            return false;
        }

        try {
            Instant lastModified = Files.getLastModifiedTime(cachePath).toInstant();
            return lastModified.plus(maxAge).isAfter(Instant.now());
        } catch (Exception exception) {
            return false;
        }
    }

    private void writeCache(Path cachePath, String content) throws Exception {
        Files.createDirectories(cachePath.getParent());
        Files.writeString(cachePath, content, StandardCharsets.UTF_8);
    }

    private Path cachePath(String namespace, String cacheKey) {
        String encodedKey = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(cacheKey.getBytes(StandardCharsets.UTF_8));
        return cacheRoot.resolve(namespace).resolve(encodedKey + ".cache");
    }

    @FunctionalInterface
    public interface CacheFetcher {
        String fetch() throws Exception;
    }
}
