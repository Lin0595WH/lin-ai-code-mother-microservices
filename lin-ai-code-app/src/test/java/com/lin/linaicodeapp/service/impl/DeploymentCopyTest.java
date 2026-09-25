package com.lin.linaicodeapp.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeploymentCopyTest {
    @Test
    void rejectsSourceLinksWithoutReplacingExistingDeployment(@TempDir Path tempDir) throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        Path target = Files.createDirectory(tempDir.resolve("deploy"));
        Files.writeString(target.resolve("index.html"), "old");
        Files.writeString(source.resolve("index.html"), "new");
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "secret");
        Files.createSymbolicLink(source.resolve("leak.txt"), secret);

        assertThrows(IOException.class, () -> AppServiceImpl.copyForDeployment(source, target));
        assertEquals("old", Files.readString(target.resolve("index.html")));
        assertFalse(Files.exists(target.resolve("leak.txt")));

        Files.delete(source.resolve("leak.txt"));
        AppServiceImpl.copyForDeployment(source, target);
        assertEquals("new", Files.readString(target.resolve("index.html")));
    }
}
