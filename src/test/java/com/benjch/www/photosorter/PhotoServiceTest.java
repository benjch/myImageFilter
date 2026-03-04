package com.benjch.www.photosorter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhotoServiceTest {

    private static final String PNG_1X1_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO0G3a4AAAAASUVORK5CYII=";

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
    void keepImageShouldUseParentFolderNameAndCoverSuffix() throws Exception {
        PhotoService service = new PhotoService(new ThumbnailCache());
        Path sourceDir = tempDir.resolve("my_game");
        Path keepDir = tempDir.resolve("keep");
        Files.createDirectories(sourceDir);
        Files.createDirectories(keepDir);

        Path source = sourceDir.resolve("image.png");
        Files.writeString(source, "fake-image-content");

        PhotoService.KeepResult result = service.keepImage(source.toString(), keepDir.toString(), "_01_cover");

        assertEquals("my_game_01_cover.jpg", result.filename());
        assertTrue(Files.exists(keepDir.resolve("my_game_01_cover.jpg")));
    }

    @Test
    void keepImageShouldUseRequestedVariantSuffixAndIncrement() throws Exception {
        PhotoService service = new PhotoService(new ThumbnailCache());
        Path sourceDir = tempDir.resolve("my_game");
        Path keepDir = tempDir.resolve("keep");
        Files.createDirectories(sourceDir);
        Files.createDirectories(keepDir);

        Path source = sourceDir.resolve("image.jpg");
        Files.writeString(source, "fake-image-content");
        Files.createFile(keepDir.resolve("my_game_03_instructions.jpg"));

        PhotoService.KeepResult result = service.keepImage(source.toString(), keepDir.toString(), "_03_instructions");

        assertEquals("my_game_03_instructions_01.jpg", result.filename());
        assertTrue(Files.exists(keepDir.resolve("my_game_03_instructions_01.jpg")));
    }

    @Test
    void importImageFromClipboardShouldUseFirstFreeNumericName() throws Exception {
        PhotoService service = new PhotoService(new ThumbnailCache());
        Path folder = tempDir.resolve("images");
        Files.createDirectories(folder);
        Files.createFile(folder.resolve("01.jpg"));
        Files.createFile(folder.resolve("02.png"));
        Files.createFile(folder.resolve("03.gif"));
        Files.createFile(folder.resolve("28.webp"));

        PhotoService.ImportedSingleImage result = service.importImageFromClipboard(folder.toString(), PNG_1X1_BASE64, "image/png");

        assertEquals("04.png", result.filename());
        assertTrue(Files.exists(folder.resolve("04.png")));
    }

    @Test
    void importFromHtmlShouldImportDataImageAndIgnoreInvalidEntries() throws Exception {
        PhotoService service = new PhotoService(new ThumbnailCache());
        Path folder = tempDir.resolve("images");
        Files.createDirectories(folder);
        String html = """
                <html><body>
                <img src=\"data:image/png;base64,%s\" />
                <img src=\"notaurl\" />
                </body></html>
                """.formatted(PNG_1X1_BASE64);

        PhotoService.HtmlImportResult result = service.importFromHtml(folder.toString(), html);

        assertEquals(1, result.importedCount());
        assertEquals(1, result.files().size());
        assertTrue(Files.exists(folder.resolve(result.files().get(0))));
    }

    @Test
    void importImageFromClipboardShouldFailOnInvalidBase64() throws Exception {
        PhotoService service = new PhotoService(new ThumbnailCache());
        Path folder = tempDir.resolve("images");
        Files.createDirectories(folder);

        assertThrows(IllegalArgumentException.class,
                () -> service.importImageFromClipboard(folder.toString(), "not-base64", "image/png"));
    }
}
