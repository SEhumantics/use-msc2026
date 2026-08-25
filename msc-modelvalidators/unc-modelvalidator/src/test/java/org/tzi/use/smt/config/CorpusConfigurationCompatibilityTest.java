package org.tzi.use.smt.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import com.google.gson.Gson;

/** Ensures every finding entry in the benchmark manifest still has a loadable selected section. */
public class CorpusConfigurationCompatibilityTest {

    @Test
    public void allThirtyThreeManifestConfigurationsLoad() throws Exception {
        Path repository = repositoryRoot();
        Path manifest = repository.resolve("msc-modelvalidators/benchmark/src/main/resources/manifest.json");
        Manifest parsed;
        try (Reader reader = Files.newBufferedReader(manifest)) {
            parsed = new Gson().fromJson(reader, Manifest.class);
        }

        int loaded = 0;
        for (Example example : parsed.examples) {
            if (!example.mode.contains("finding")) {
                continue;
            }
            assertNotNull(example.id + ": propertiesFile", example.propertiesFile);
            Path configuration = repository.resolve("msc-modelvalidators/benchmark/examples")
                    .resolve(example.directory).resolve(example.propertiesFile);
            ConfigurationReader.read(configuration, example.section);
            loaded++;
        }

        assertEquals("manifest corpus size", 33, loaded);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("msc-modelvalidators/benchmark/src/main/resources/manifest.json"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("repository root not found from test working directory");
    }

    private static final class Manifest {
        List<Example> examples;
    }

    private static final class Example {
        String id;
        String directory;
        String propertiesFile;
        String section;
        String mode;
    }
}
