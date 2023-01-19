package ru.mlukin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class FileUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class.getName());

    /**
     * Starts one-way synchronization of two entries. After it, target should be equal to source
     *
     * @param source source entry
     * @param target target entry
     * @throws IOException if an I/O error occurs
     */
    public static void sync(File source, File target) throws IOException {
        if (source.isDirectory() && !Files.isSymbolicLink(source.toPath())) {
            if (!target.exists()) {
                if (!target.mkdirs()) {
                    throw new IOException("Failed to create directory " + target);
                }
            }
            Set<String> sourceFiles = new HashSet<>(toList(source.list()));
            for (String targetFile : toList(target.list())) {
                if (!sourceFiles.contains(targetFile)) {
                    del(new File(target, targetFile));
                }
            }
            for (String sourceFile : sourceFiles) {
                sync(new File(source, sourceFile), new File(target, sourceFile));
            }
        } else {
            if (target.exists() && (source.lastModified() == target.lastModified()) && (source.length() == target.length())) {
                return;
            }
            Files.copy(source.toPath(), target.toPath(), LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Deletes specified file or directory with its content
     *
     * @param file the entry to be deleted
     */
    public static void del(File file) {
        if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
            for (File f : toList(file.listFiles())) {
                del(f);
            }
        }
        if (!file.delete()) {
            LOGGER.warn("Failed to delete {}", file);
        }
    }

    /**
     * Returns a list backed by specified array or empty list if the array is null
     *
     * @param arr the array by which the list will be backed
     * @param <T> the class of the objects in the array
     * @return a list view of the specified array
     */
    private static <T> List<T> toList(T[] arr) {
        return arr == null ? Collections.emptyList() : Arrays.asList(arr);
    }
}
