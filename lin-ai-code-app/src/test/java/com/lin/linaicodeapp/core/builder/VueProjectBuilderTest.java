package com.lin.linaicodeapp.core.builder;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class VueProjectBuilderTest {
    @Test
    void rejectsMissingProjectOrPackageJson(@TempDir Path tempDir) throws Exception {
        assertFalse(new VueProjectBuilder().buildProject(tempDir.toString()));
    }
}
