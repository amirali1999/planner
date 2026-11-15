package com.lifeplanner.db;

import com.lifeplanner.config.PlannerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: the shipped default "../data/planner.db" failed to bind in a web context because String→Path
 * conversion went through the servlet resource loader ("/../data" normalizes to null). A ".." path must bind (real embedded server, not MockMvc)
 * as a plain filesystem path relative to the working directory. Stays inside target/ (working dir = backend/).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "planner.datasource.path=../backend/target/test-db/dotdot-${random.uuid}.db")
class RelativeDbPathTest {

    @Autowired
    PlannerProperties properties;

    @Test
    void parentRelativePathBindsAsFilesystemPathInWebContext() {
        Path configured = properties.datasource().file();
        assertThat(configured.toString()).startsWith("..");
        Path resolved = configured.toAbsolutePath().normalize();
        assertThat(resolved.getParent()).isEqualTo(Path.of("target/test-db").toAbsolutePath().normalize());
        assertThat(Files.exists(resolved)).isTrue();
    }
}
