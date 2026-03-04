package com.benjch.www.photosorter;

import java.awt.Desktop;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class PhotoService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final ThumbnailCache thumbnailCache;

    public PhotoService(ThumbnailCache thumbnailCache) {
        this.thumbnailCache = thumbnailCache;
    }

    public FolderEntries listFolderEntries(String rawPath) throws IOException {
        if (rawPath == null || rawPath.isBlank()) {
            List<FolderEntry> roots = new ArrayList<>();
            for (Path root : FileSystems.getDefault().getRootDirectories()) {
                roots.add(new FolderEntry(root.toString(), root.toString(), Instant.EPOCH.toEpochMilli()));
            }
            roots.sort(Comparator.comparing(FolderEntry::name, String.CASE_INSENSITIVE_ORDER));
            return new FolderEntries("", List.of(), roots);
        }

        Path folder = resolveSafePath(rawPath);
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Not a directory: " + rawPath);
        }

        List<ImageEntry> images = new ArrayList<>();
        List<FolderEntry> folders = new ArrayList<>();

        try (Stream<Path> stream = Files.list(folder)) {
            stream.forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        folders.add(new FolderEntry(path.toString(), path.getFileName().toString(), Files.getLastModifiedTime(path).toMillis()));
                    } else if (isImage(path)) {
                        ImageSize imageSize = readImageSize(path);
                        images.add(new ImageEntry(path.toString(), path.getFileName().toString(), Files.getLastModifiedTime(path).toMillis(), imageSize.width(), imageSize.height(), extensionOf(path.getFileName().toString()).replace(".", "")));
                    }
                } catch (IOException ignored) {
                    // ignore unreadable entries
                }
            });
        }

        images.sort(Comparator.comparing(ImageEntry::name, String.CASE_INSENSITIVE_ORDER));
        folders.sort(Comparator.comparing(FolderEntry::name, String.CASE_INSENSITIVE_ORDER));

        return new FolderEntries(folder.toString(), images, folders);
    }

    public byte[] loadImage(String rawPath) throws IOException {
        Path path = resolveSafePath(rawPath);
        return Files.readAllBytes(path);
    }

    public byte[] loadThumbnail(String rawPath, int size) throws IOException {
        Path path = resolveSafePath(rawPath);
        long mtime = Files.getLastModifiedTime(path).toMillis();
        String key = path + "|" + mtime + "|" + size;
        byte[] cached = thumbnailCache.get(key);
        if (cached != null) {
            return cached;
        }

        byte[] originalBytes = Files.readAllBytes(path);
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalBytes));
        byte[] output;
        if (original == null) {
            output = originalBytes;
        } else {
            int width = original.getWidth();
            int height = original.getHeight();
            double ratio = Math.min((double) size / width, (double) size / height);
            int targetWidth = Math.max(1, (int) Math.round(width * ratio));
            int targetHeight = Math.max(1, (int) Math.round(height * ratio));
            Image resized = original.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
            BufferedImage buffer = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            buffer.getGraphics().drawImage(resized, 0, 0, null);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(buffer, "jpg", byteArrayOutputStream);
            output = byteArrayOutputStream.toByteArray();
        }

        thumbnailCache.put(key, output);
        return output;
    }

    public void deleteImage(String rawPath) throws IOException {
        Path path = resolveSafePath(rawPath);
        if (!isImage(path)) {
            throw new IllegalArgumentException("Delete only allowed on image files");
        }

        boolean movedToTrash = false;
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                movedToTrash = desktop.moveToTrash(path.toFile());
            }
        }
        if (!movedToTrash) {
            Files.deleteIfExists(path);
        }
        thumbnailCache.invalidateByPrefix(path.toString());
    }

    public KeepResult keepImage(String rawPath, String keepDir, String keepLabelSuffix) throws IOException {
        Path source = resolveSafePath(rawPath);
        if (!isImage(source)) {
            throw new IllegalArgumentException("Keep only allowed on image files");
        }

        Path keepPath = resolveSafePath(keepDir);
        Files.createDirectories(keepPath);
        String folderName = source.getParent() != null && source.getParent().getFileName() != null
                ? source.getParent().getFileName().toString()
                : baseNameWithoutExtension(source.getFileName().toString());
        String suffix = keepLabelSuffix == null ? "" : keepLabelSuffix;
        Path target = findAvailableName(keepPath, folderName + suffix, ".jpg");

        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        return new KeepResult(target.toString(), target.getFileName().toString());
    }

    Path findAvailableName(Path keepDir, String base, String extension) {
        Path first = keepDir.resolve(base + extension);
        if (!Files.exists(first)) {
            return first;
        }
        int counter = 1;
        while (true) {
            String suffix = counter < 100 ? String.format("_%02d", counter) : "_" + counter;
            Path candidate = keepDir.resolve(base + suffix + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    public static boolean isImage(Path path) {
        String ext = extensionOf(path.getFileName().toString()).replace(".", "");
        return SUPPORTED_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT));
    }


    private static String baseNameWithoutExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx <= 0) {
            return fileName;
        }
        return fileName.substring(0, idx);
    }

    private static String extensionOf(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0) {
            return "";
        }
        return fileName.substring(idx).toLowerCase(Locale.ROOT);
    }

    private ImageSize readImageSize(Path path) {
        try (InputStream inputStream = Files.newInputStream(path);
                ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {
            if (imageInputStream == null) {
                return new ImageSize(0, 0);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                return new ImageSize(0, 0);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream, true, true);
                return new ImageSize(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            return new ImageSize(0, 0);
        }
    }

    public static String contentTypeFor(String rawPath) {
        String ext = extensionOf(rawPath);
        return switch (ext) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    public static Path resolveSafePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("Path required");
        }
        Path path = Path.of(rawPath).toAbsolutePath().normalize();
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Absolute path required");
        }
        return path;
    }

    public record FolderEntries(String currentPath, List<ImageEntry> images, List<FolderEntry> folders) {
    }

    public record ImageEntry(String path, String name, long modifiedAt, int width, int height, String extension) {
    }

    public record FolderEntry(String path, String name, long modifiedAt) {
    }

    public record KeepResult(String path, String filename) {
    }

    private record ImageSize(int width, int height) {
    }
}
