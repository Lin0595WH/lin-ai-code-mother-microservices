package com.lin.linaicodeapp.core.builder;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VueProjectBuilderTest {
    @Test
    void rejectsMissingProjectOrPackageJson(@TempDir Path tempDir) {
        assertFalse(new VueProjectBuilder("", new MockEnvironment()).buildProject(tempDir.toString()));
    }

    @Test
    void refusesLocalBuildWithoutExplicitDevelopmentProfile(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("package.json"), "{}");
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod", "cloud");
        assertFalse(new VueProjectBuilder("", production).buildProject(tempDir.toString()));
        assertFalse(new VueProjectBuilder("", new MockEnvironment()).buildProject(tempDir.toString()));
    }

    @Test
    void sendsOnlySourceAndInstallsWorkerDist(@TempDir Path tempDir) throws Exception {
        Path project = tempDir.resolve("vue_project_1");
        Files.createDirectories(project.resolve("src"));
        Files.createDirectories(project.resolve("node_modules"));
        Files.createDirectories(project.resolve("dist"));
        Files.writeString(project.resolve("package.json"), "{}");
        Files.writeString(project.resolve("src/App.vue"), "<template>new</template>");
        Files.writeString(project.resolve("node_modules/private.txt"), "other user data");
        Files.writeString(project.resolve("dist/stale.txt"), "stale");

        Set<String> received = new HashSet<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/build", exchange -> {
            try (ZipInputStream input = new ZipInputStream(exchange.getRequestBody())) {
                ZipEntry entry;
                while ((entry = input.getNextEntry()) != null) received.add(entry.getName());
            }
            byte[] output = zip("./index.html", "<html>built</html>");
            exchange.sendResponseHeaders(200, output.length);
            exchange.getResponseBody().write(output);
            exchange.close();
        });
        server.start();
        try {
            assertTrue(new VueProjectBuilder("http://127.0.0.1:" + server.getAddress().getPort(), new MockEnvironment())
                    .buildProject(project.toString()));
        } finally {
            server.stop(0);
        }

        assertEquals(Set.of("package.json", "src/App.vue"), received);
        assertEquals("<html>built</html>", Files.readString(project.resolve("dist/index.html")));
        assertFalse(Files.exists(project.resolve("dist/stale.txt")));
    }

    @Test
    void rejectsSymlinkAndTraversalOutput(@TempDir Path tempDir) throws Exception {
        Path project = tempDir.resolve("vue_project_2");
        Files.createDirectories(project);
        Files.writeString(project.resolve("package.json"), "{}");
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "secret");
        Files.createSymbolicLink(project.resolve("leak.txt"), secret);
        assertFalse(new VueProjectBuilder("http://127.0.0.1:9", new MockEnvironment()).buildProject(project.toString()));
        Files.delete(project.resolve("leak.txt"));

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/build", exchange -> {
            exchange.getRequestBody().transferTo(java.io.OutputStream.nullOutputStream());
            byte[] output = zip("../leak.txt", "leak");
            exchange.sendResponseHeaders(200, output.length);
            exchange.getResponseBody().write(output);
            exchange.close();
        });
        server.start();
        try {
            assertFalse(new VueProjectBuilder("http://127.0.0.1:" + server.getAddress().getPort(), new MockEnvironment())
                    .buildProject(project.toString()));
        } finally {
            server.stop(0);
        }
        assertEquals("secret", Files.readString(secret));
    }

    private byte[] zip(String name, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            if (name.startsWith("./")) {
                zip.putNextEntry(new ZipEntry("./"));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
