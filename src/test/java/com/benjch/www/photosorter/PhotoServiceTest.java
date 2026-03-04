package com.benjch.www.photosorter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhotoServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void keepNameShouldIncrementWithoutOverwrite() throws Exception {
        PhotoService service = new PhotoService(new ThumbnailCache());
        Path keepDir = tempDir.resolve("keep");
        Files.createDirectories(keepDir);

        Path first = service.findAvailableName(keepDir, "toto", ".jpg");
        assertEquals("toto.jpg", first.getFileName().toString());

        Files.createFile(keepDir.resolve("toto.jpg"));
        Path second = service.findAvailableName(keepDir, "toto", ".jpg");
        assertEquals("toto_01.jpg", second.getFileName().toString());

        Files.createFile(keepDir.resolve("toto_01.jpg"));
        Files.createFile(keepDir.resolve("toto_02.jpg"));
        Path third = service.findAvailableName(keepDir, "toto", ".jpg");
        assertEquals("toto_03.jpg", third.getFileName().toString());
    }

    @Test
    void keepImageShouldUseParentFolderNameAsBase() throws Exception {
        PhotoService service = new PhotoService(new ThumbnailCache());
        Path sourceDir = tempDir.resolve("1988_10_29_Japan_Space Harrier II");
        Path keepDir = tempDir.resolve("megadrive_unique");
        Files.createDirectories(sourceDir);
        Files.createDirectories(keepDir);

        Path source = sourceDir.resolve("00.jpg");
        Files.writeString(source, "fake-image-content");

        PhotoService.KeepResult result = service.keepImage(source.toString(), keepDir.toString());

        assertEquals("1988_10_29_Japan_Space Harrier II.jpg", result.filename());
        assertTrue(Files.exists(keepDir.resolve("1988_10_29_Japan_Space Harrier II.jpg")));
    }

    @Test
    void keepImageShouldIncrementParentFolderBasedName() throws Exception {
        PhotoService service = new PhotoService(new ThumbnailCache());
        Path sourceDir = tempDir.resolve("1988_10_29_Japan_Space Harrier II");
        Path keepDir = tempDir.resolve("megadrive_unique");
        Files.createDirectories(sourceDir);
        Files.createDirectories(keepDir);

        Path source = sourceDir.resolve("00.jpg");
        Files.writeString(source, "fake-image-content");
        Files.createFile(keepDir.resolve("1988_10_29_Japan_Space Harrier II.jpg"));

        PhotoService.KeepResult result = service.keepImage(source.toString(), keepDir.toString());

        assertEquals("1988_10_29_Japan_Space Harrier II_01.jpg", result.filename());
    }
}
