package ru.mlukin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;


class ReplicatorTest {
    private static final Path SOURCE_DIR = Paths.get("tmp1");
    private static final Path TARGET_DIR = Paths.get("tmp2");
    private static final Path SOURCE_FILE = SOURCE_DIR.resolve("tmp1.txt");
    private Replicator replicator;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectory(SOURCE_DIR);
        replicator = new WSOReplicator(SOURCE_DIR, TARGET_DIR);
    }

    @AfterEach
    void tearDown() {
        replicator.stop();
        FileUtils.del(SOURCE_DIR.toFile());
        FileUtils.del(TARGET_DIR.toFile());
    }

    @Test
    void replicateFileCreation() throws InterruptedException, IOException {
        replicator.start();
        Files.createFile(SOURCE_FILE);
        Thread.sleep(WSOReplicator.POLL_INTERVAL + 500);
        assertAll(
                () -> assertTrue(Files.exists(TARGET_DIR)),
                () -> assertTrue(Files.exists(TARGET_DIR.resolve(SOURCE_DIR.relativize(SOURCE_FILE))))
        );
    }

    @Test
    void replicateFileDeletion() throws InterruptedException, IOException {
        Files.createFile(SOURCE_FILE);
        replicator.start();
        Files.deleteIfExists(SOURCE_FILE);
        Thread.sleep(WSOReplicator.POLL_INTERVAL + 500);
        assertAll(
                () -> assertTrue(Files.exists(TARGET_DIR)),
                () -> assertFalse(Files.exists(TARGET_DIR.resolve(SOURCE_DIR.relativize(SOURCE_FILE))))
        );
    }

    @Test
    void replicateFileModification() throws InterruptedException, IOException {
        Files.createFile(SOURCE_FILE);
        replicator.start();
        String content = "Hello";
        Files.writeString(SOURCE_FILE, content);
        Thread.sleep(WSOReplicator.POLL_INTERVAL + 500);
        assertAll(
                () -> assertTrue(Files.exists(TARGET_DIR)),
                () -> assertTrue(Files.exists(TARGET_DIR.resolve(SOURCE_DIR.relativize(SOURCE_FILE)))),
                () -> assertEquals(content, Files.readString(TARGET_DIR.resolve(SOURCE_DIR.relativize(SOURCE_FILE))))
        );
    }

    @Test
    void replicateFileRenaming() throws InterruptedException, IOException {
        Files.createFile(SOURCE_FILE);
        replicator.start();
        Path renamed = Paths.get("newTmp.txt");
        Files.move(SOURCE_FILE, SOURCE_FILE.resolveSibling(renamed));
        Thread.sleep(WSOReplicator.POLL_INTERVAL + 500);
        assertAll(
                () -> assertTrue(Files.exists(TARGET_DIR)),
                () -> assertTrue(Files.exists(TARGET_DIR.resolve(renamed)))
        );
    }
}