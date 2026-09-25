package com.lin.linaicodeapp.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectDownloadServiceImplTest {
    @Test
    void excludesLinksFromDownload(@TempDir Path tempDir) throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("project"));
        Files.writeString(source.resolve("index.html"), "site");
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "secret");
        Files.createSymbolicLink(source.resolve("leak.txt"), secret);

        MockHttpServletResponse response = new MockHttpServletResponse();
        new ProjectDownloadServiceImpl().downloadProjectAsZip(source.toString(), "project", response);
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) names.add(entry.getName());
        }
        assertTrue(names.stream().anyMatch(name -> name.endsWith("index.html")));
        assertFalse(names.stream().anyMatch(name -> name.endsWith("leak.txt") || name.endsWith("secret.txt")));
    }
}
