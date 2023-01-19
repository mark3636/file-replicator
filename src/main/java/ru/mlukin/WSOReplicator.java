package ru.mlukin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WSOReplicator implements Replicator {
    public static final int POLL_INTERVAL = 500;
    private static final Logger LOGGER = LoggerFactory.getLogger(WSOReplicator.class.getName());
    private final Path sourceRoot;
    private final Path targetRoot;
    private final Set<Path> created = new LinkedHashSet<>();
    private final Set<Path> updated = new LinkedHashSet<>();
    private final Set<Path> deleted = new LinkedHashSet<>();
    private final Map<WatchKey, Path> watchKeys = new HashMap<>();
    private WatchService watchService;
    private ExecutorService watchExecutor;
    private ExecutorService syncExecutor;

    public WSOReplicator(String source, String target) {
        this(Paths.get(source), Paths.get(target));
    }

    public WSOReplicator(Path source, Path target) {
        if (!Files.isDirectory(source)) {
            LOGGER.error("Source directory {} doesn't exist", source);
            System.exit(1);
        }
        sourceRoot = source;
        targetRoot = target;
    }

    @Override
    public void start() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerDirs(sourceRoot, watchService);
            FileUtils.sync(sourceRoot.toFile(), targetRoot.toFile());
        } catch (IOException e) {
            LOGGER.error("Exception occurred during replicator start-up", e);
            System.exit(1);
        }
        watchExecutor = Executors.newSingleThreadExecutor();
        syncExecutor = Executors.newSingleThreadExecutor();
        watchExecutor.execute(this::watchChanges);
    }

    @Override
    public void stop() {
        if (watchExecutor != null) {
            watchExecutor.shutdown();
        }
    }

    /**
     * Performs all the needed actions based on the specified paths
     *
     * @param deleted  the entries to be synchronized as deleted
     * @param created  the entries to be synchronized as created
     * @param modified the entries to be synchronized as modified
     */
    private void syncChanges(Set<Path> deleted, Set<Path> created, Set<Path> modified) {
        LOGGER.info("Syncing {} deletions, {} creations, {} modifications", deleted.size(), created.size(), modified.size());
        for (Path path : deleted) {
            FileUtils.del(resolveTarget(path).toFile());
        }
        for (Path path : created) {
            try {
                FileUtils.sync(path.toFile(), resolveTarget(path).toFile());
            } catch (IOException e) {
                LOGGER.error("Exception {} during sync {}", e, path);
            }
        }
        for (Path path : modified)
            if (Files.isRegularFile(path)) {
                try {
                    FileUtils.sync(path.toFile(), resolveTarget(path).toFile());
                } catch (IOException e) {
                    LOGGER.error("Exception {} during sync {}", e, path);
                }
            }
    }

    /**
     * Triggers synchronization process
     *
     * @param deleted  the entries to be synchronized as deleted
     * @param created  the entries to be synchronized as created
     * @param modified the entries to be synchronized as modified
     */
    private void triggerSync(Set<Path> deleted, Set<Path> created, Set<Path> modified) {
        Set<Path> delCopy = new HashSet<>(deleted);
        Set<Path> creCopy = new HashSet<>(created);
        Set<Path> modCopy = new HashSet<>(modified);
        syncExecutor.execute(() -> syncChanges(delCopy, creCopy, modCopy));
        if (watchExecutor.isShutdown()) {
            syncExecutor.shutdown();
        }
    }

    /**
     * Watches the changes on the file system and stores them for further processing
     */
    private void watchChanges() {
        try (WatchService ignored = watchService) {
            while (!watchKeys.isEmpty()) {
                boolean hasPending = created.size() + updated.size() + deleted.size() > 0;
                WatchKey watchKey = hasPending ? watchService.poll(POLL_INTERVAL, TimeUnit.MILLISECONDS) : watchService.take();
                if (watchKey != null) {
                    Path parent = watchKeys.get(watchKey);
                    for (WatchEvent<?> event : watchKey.pollEvents()) {
                        Path eventPath = (Path) event.context();
                        storeEvent(event.kind(), parent.resolve(eventPath));
                    }
                    boolean valid = watchKey.reset();
                    if (!valid) {
                        watchKeys.remove(watchKey);
                    }
                } else if (hasPending) {
                    triggerSync(deleted, created, updated);
                    deleted.clear();
                    created.clear();
                    updated.clear();
                }
            }
        } catch (InterruptedException | ClosedWatchServiceException e) {
            triggerSync(deleted, created, updated);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Stores event for further processing with the following optimization:
     * <li>DELETE followed by CREATE           => stores as MODIFY
     * <li>CREATE followed by MODIFY           => stores as CREATE
     * <li>CREATE or MODIFY followed by DELETE => stores as DELETE
     *
     * @param kind type of the event
     * @param path the path for which the event occurred
     */
    private void storeEvent(WatchEvent.Kind<?> kind, Path path) {
        boolean toCreate = false;
        boolean toUpdate = false;
        boolean toDelete = kind == StandardWatchEventKinds.ENTRY_DELETE;
        try {
            if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                LOGGER.info("Registering {}", path);
                registerDir(path, watchService);
            }
        } catch (IOException e) {
            LOGGER.error("Exception {} during registering dir {}", e, path);
        }
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            toUpdate = deleted.contains(path);
            toCreate = !toUpdate;
        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            toCreate = created.contains(path);
            toUpdate = !toCreate;
        }
        addOrRemove(created, toCreate, path);
        addOrRemove(updated, toUpdate, path);
        addOrRemove(deleted, toDelete, path);
    }

    /**
     * Adds or removes the specified path from the specified set depending on the shouldAdd flag
     *
     * @param set       the set to be changed
     * @param shouldAdd the flag indicating should the path be added or removed
     * @param path      the path to be added or removed
     */
    private void addOrRemove(Set<Path> set, boolean shouldAdd, Path path) {
        if (shouldAdd) set.add(path);
        else set.remove(path);
    }

    /**
     * Registers the specified directory and all its subdirectories with the specified watch service
     * for the {@link  StandardWatchEventKinds#ENTRY_CREATE}, {@link  StandardWatchEventKinds#ENTRY_DELETE}
     * and {@link  StandardWatchEventKinds#ENTRY_MODIFY} events
     *
     * @param start        the directory to start registration with
     * @param watchService the watch service for which the directory is registered
     * @throws IOException if an I/O error occurs
     */
    private void registerDirs(Path start, WatchService watchService) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerDir(dir, watchService);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Registers the specified directory with the specified watch service
     * for the {@link  StandardWatchEventKinds#ENTRY_CREATE}, {@link  StandardWatchEventKinds#ENTRY_DELETE}
     * and {@link  StandardWatchEventKinds#ENTRY_MODIFY} events
     *
     * @param dir          the directory to be watched for events
     * @param watchService the watch service for which the directory is registered
     * @throws IOException if an I/O error occurs
     */
    private void registerDir(Path dir, WatchService watchService) throws IOException {
        LOGGER.info("Registering {}", dir);
        watchKeys.put(dir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY), dir);
    }

    /**
     * Resolves the target path against the source path.
     *
     * @param source the source path
     * @return the resulting path
     */
    private Path resolveTarget(Path source) {
        return targetRoot.resolve(sourceRoot.relativize(source));
    }
}