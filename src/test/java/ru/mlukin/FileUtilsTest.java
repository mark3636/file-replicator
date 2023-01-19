package ru.mlukin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilsTest {
    private static final Path SOURCE_DIR = Paths.get("tmp1");
    private static final Path TARGET_DIR = Paths.get("tmp2");
    private static final Path SOURCE_FILE = SOURCE_DIR.resolve("tmp1.txt");

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectory(SOURCE_DIR);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(SOURCE_FILE);
        Files.deleteIfExists(SOURCE_DIR);
        Files.deleteIfExists(TARGET_DIR.resolve(SOURCE_DIR.relativize(SOURCE_FILE)));
        Files.deleteIfExists(TARGET_DIR);
    }

    @Test
    void deleteFile() throws IOException {
        Files.createFile(SOURCE_FILE);
        FileUtils.del(SOURCE_FILE.toFile());
        assertAll(
                () -> assertFalse(Files.exists(SOURCE_FILE)),
                () -> assertTrue(Files.exists(SOURCE_DIR))
        );
    }

    @Test
    void deleteEmptyDirectory() {
        FileUtils.del(SOURCE_DIR.toFile());
        assertFalse(Files.exists(SOURCE_DIR));
    }

    @Test
    void deleteNonEmptyDirectory() {
        FileUtils.del(SOURCE_DIR.toFile());
        assertFalse(Files.exists(SOURCE_DIR));
    }

    @Test
    void syncEmptyDirectoryWithNonExistent() throws IOException {
        FileUtils.sync(SOURCE_DIR.toFile(), TARGET_DIR.toFile());
        assertAll(
                () -> assertTrue(Files.exists(TARGET_DIR)),
                () -> assertTrue(Files.isDirectory(TARGET_DIR))
        );
    }

    @Test
    void syncNonEmptyDirectoryWithNonExistent() throws IOException {
        Files.createFile(SOURCE_FILE);
        FileUtils.sync(SOURCE_DIR.toFile(), TARGET_DIR.toFile());
        assertAll(
                () -> assertTrue(Files.exists(TARGET_DIR)),
                () -> assertTrue(Files.exists(TARGET_DIR.resolve(SOURCE_DIR.relativize(SOURCE_FILE))))
        );
    }

    @Test
    void syncEmptyDirectoryWithEmptyExistent() throws IOException {
        Files.createDirectory(TARGET_DIR);
        FileUtils.sync(SOURCE_DIR.toFile(), TARGET_DIR.toFile());
        assertTrue(Files.exists(TARGET_DIR));
    }

    @Test
    void syncNonEmptyDirectoryWithEmptyExistent() throws IOException {
        Files.createFile(SOURCE_FILE);
        Files.createDirectory(TARGET_DIR);
        FileUtils.sync(SOURCE_DIR.toFile(), TARGET_DIR.toFile());
        assertAll(
                () -> assertTrue(Files.exists(TARGET_DIR)),
                () -> assertTrue(Files.exists(TARGET_DIR.resolve(SOURCE_DIR.relativize(SOURCE_FILE))))
        );
    }

    @Test
    void syncEmptyDirectoryWithNonEmptyExistent() throws IOException {
        Files.createDirectory(TARGET_DIR);
        Files.createFile(TARGET_DIR.resolve(SOURCE_DIR.relativize(SOURCE_FILE)));
        FileUtils.sync(SOURCE_DIR.toFile(), TARGET_DIR.toFile());
        assertAll(
                () -> assertTrue(Files.exists(TARGET_DIR)),
                () -> assertFalse(Files.exists(TARGET_DIR.resolve(SOURCE_DIR.relativize(SOURCE_FILE))))
        );
    }

    @Test
    void syncNonEmptyDirectoryWithNonEmptyExistent() throws IOException {
        Files.createFile(SOURCE_FILE);
        Files.createDirectory(TARGET_DIR);
        Files.createFile(TARGET_DIR.resolve(SOURCE_DIR.relativize(SOURCE_FILE)));
        FileUtils.sync(SOURCE_DIR.toFile(), TARGET_DIR.toFile());
        assertAll(
                () -> assertTrue(Files.exists(TARGET_DIR)),
                () -> assertTrue(Files.exists(TARGET_DIR.resolve(SOURCE_DIR.relativize(SOURCE_FILE))))
        );
    }
}