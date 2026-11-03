package com.axvon.hrms.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Where the policy index and its catalogue live.
 *
 * Overridable through {@code axvon.ai.index-dir} for exactly one reason: the test suite
 * must never touch {@code ~/.axvon}. The real index sits beside somebody's live HR
 * database, and a test that ingested a sample PDF into it would silently replace the
 * company's actual policy documents — the same hazard the in-memory test datasource
 * exists to avoid. Tests point this at a temporary directory instead.
 */
@Component
public class AiPaths {

    private final Path dir;

    public AiPaths(@Value("${axvon.ai.index-dir:}") String configured) {
        this.dir = (configured == null || configured.isBlank())
            ? Paths.get(System.getProperty("user.home"), ".axvon", "ai")
            : Paths.get(configured.trim());
    }

    public Path directory() {
        return dir;
    }

    /** The serialised vectors, written whole after each ingest. */
    public File indexFile() {
        return dir.resolve("policy-index.json").toFile();
    }

    /** What has been ingested — the vector store itself cannot be enumerated. */
    public File catalogueFile() {
        return dir.resolve("policy-catalogue.json").toFile();
    }
}
