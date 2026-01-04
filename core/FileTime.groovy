package core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import groovy.transform.Synchronized

/**
 * Session-scoped file modification tracking utility.
 * 
 * Tracks when files are read and ensures they haven't been modified before writing.
 * Prevents lost updates in concurrent scenarios.
 */
@Singleton
class FileTime {
    
    private final Map<String, FileTimeInfo> fileTimestamps = new ConcurrentHashMap<>()
    private final Map<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>()
    
    /**
     * Record that a file was read at a specific time.
     * @param filePath Absolute path to the file
     */
    @Synchronized
    void read(String filePath) {
        Path path = Paths.get(filePath).normalize()
        if (Files.exists(path)) {
            def modTime = Files.getLastModifiedTime(path)
            fileTimestamps[filePath] = new FileTimeInfo(modTime.toMillis(), new Date())
        }
    }
    
    /**
     * Assert that the file hasn't been modified since it was read.
     * @param filePath Absolute path to the file
     * @throws RuntimeException if file was modified externally
     */
    @Synchronized
    void checkUnmodified(String filePath) {
        def recorded = fileTimestamps[filePath]
        if (recorded == null) {
            return // File wasn't read, no need to check
        }
        
        Path path = Paths.get(filePath).normalize()
        if (!Files.exists(path)) {
            return // File was deleted, can't check
        }
        
        def currentModTime = Files.getLastModifiedTime(path).toMillis()
        
        if (currentModTime != recorded.modificationTime) {
            throw new RuntimeException(
                "File ${filePath} was modified externally after being read. " +
                "Last read: ${recorded.readTime}. " +
                "Please re-read the file before writing."
            )
        }
    }
    
    /**
     * Execute a closure with a lock on the file to prevent concurrent modifications.
     * @param filePath Absolute path to the file
     * @param closure Closure to execute while holding the lock
     * @return Result of the closure
     */
    def withLock(String filePath, Closure closure) {
        def lock = fileLocks.computeIfAbsent(filePath, { new ReentrantLock() })
        try {
            lock.lock()
            return closure.call()
        } finally {
            lock.unlock()
        }
    }
    
    /**
     * Clear all recorded timestamps (useful for testing or session reset).
     */
    @Synchronized
    void clear() {
        fileTimestamps.clear()
        fileLocks.clear()
    }
    
    /**
     * Get the number of tracked files.
     * @return Number of tracked files
     */
    @Synchronized
    int getTrackedFileCount() {
        return fileTimestamps.size()
    }
    
    /**
     * Check if a file is currently tracked.
     * @param filePath Absolute path to the file
     * @return true if file is tracked, false otherwise
     */
    @Synchronized
    boolean isTracked(String filePath) {
        return fileTimestamps.containsKey(filePath)
    }
    
    /**
     * Internal class to store file time information.
     */
    private static class FileTimeInfo {
        final long modificationTime
        final Date readTime
        
        FileTimeInfo(long modificationTime, Date readTime) {
            this.modificationTime = modificationTime
            this.readTime = readTime
        }
    }
}
