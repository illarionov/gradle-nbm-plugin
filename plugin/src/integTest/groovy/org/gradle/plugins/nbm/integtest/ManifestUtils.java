package org.gradle.plugins.nbm.integtest;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public final class ManifestUtils {
    public static Map<String, String> readManifest(Path path) throws IOException {
        try (InputStream fileInput = Files.newInputStream(path);
            InputStream input = new BufferedInputStream(fileInput)) {
            Manifest manifest = new Manifest(input);
            return manifest.getMainAttributes().entrySet().stream()
                .collect(Collectors.toMap(mapEntry -> mapEntry.getKey().toString(),
                    mapEntry -> mapEntry.getValue().toString(), (x, y) -> y, LinkedHashMap::new));
        }
    }

    private ManifestUtils() {
        throw new AssertionError();
    }
}
