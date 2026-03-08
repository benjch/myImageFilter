package com.benjch.www.photosorter;

import java.awt.Desktop;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class PhotoService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile("<img\\b[^>]*\\bsrc\\s*=\\s*(['\"])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ANCHOR_HREF_PATTERN = Pattern.compile("<a\\b[^>]*\\bhref\\s*=\\s*(['\"])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern GOOGLE_IMAGE_URL_PATTERN = Pattern.compile("https?://[^\"\'\s<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOOGLE_RESULT_LINK_PATTERN = Pattern.compile("(?:/imgres\\?|https?://www\\.google\\.[^/]+/imgres\\?)[^\"\'\\s<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_CONTENT_PATTERN = Pattern.compile("<meta\\b[^>]*\\b(?:property|name)\\s*=\\s*(['\"])(?:og:image|twitter:image)\\1[^>]*\\bcontent\\s*=\\s*(['\"])(.*?)\\2", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final int MAX_CLIPBOARD_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final int DEFAULT_GOOGLE_SCRAP_PARALLELISM = 50;
    private static final int MAX_GOOGLE_SCRAP_PARALLELISM = 50;

    private final ThumbnailCache thumbnailCache;

    public PhotoService(ThumbnailCache thumbnailCache) {
        this.thumbnailCache = thumbnailCache;
    }

    public FolderEntries listFolderEntries(String rawPath) throws IOException {
        if (rawPath == null || rawPath.isBlank()) {
            List<FolderEntry> roots = new ArrayList<>();
            for (Path root : FileSystems.getDefault().getRootDirectories()) {
                roots.add(new FolderEntry(root.toString(), root.toString(), Instant.EPOCH.toEpochMilli(), 0));
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
                        folders.add(new FolderEntry(
                                path.toString(),
                                path.getFileName().toString(),
                                Files.getLastModifiedTime(path).toMillis(),
                                countImagesInFolder(path)));
                    } else if (isImage(path)) {
                        ImageSize imageSize = readImageSize(path);
                        images.add(new ImageEntry(
                                path.toString(),
                                path.getFileName().toString(),
                                Files.getLastModifiedTime(path).toMillis(),
                                imageSize.width(),
                                imageSize.height(),
                                extensionOf(path.getFileName().toString()).replace(".", ""),
                                Files.size(path)
                        ));
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

    public void clearThumbnailCache() {
        thumbnailCache.invalidateAll();
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

    public HtmlImportResult importFromHtml(String folderPath, String html) throws IOException {
        Path folder = resolveSafePath(folderPath);
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Not a directory: " + folderPath);
        }
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("HTML content is required");
        }

        List<String> files = new ArrayList<>();
        for (String src : extractImportSourcesFromHtml(html)) {
            try {
                ImportedImage imported = loadImageFromSrc(src);
                Path target = findAvailableName(folder, imported.baseName(), "." + imported.extension());
                Files.write(target, imported.bytes());
                files.add(target.getFileName().toString());
            } catch (Exception ignored) {
                // continue on invalid source
            }
        }

        return new HtmlImportResult(files.size(), files);
    }

    List<String> extractImportSourcesFromHtml(String html) {
        Set<String> sources = new java.util.LinkedHashSet<>();
        String safeHtml = html == null ? "" : html;

        Matcher imageMatcher = IMG_SRC_PATTERN.matcher(safeHtml);
        while (imageMatcher.find()) {
            String src = imageMatcher.group(2) == null ? "" : imageMatcher.group(2).trim();
            if (!src.isBlank()) {
                sources.add(src);
            }
        }

        Matcher hrefMatcher = ANCHOR_HREF_PATTERN.matcher(safeHtml);
        while (hrefMatcher.find()) {
            String href = hrefMatcher.group(2) == null ? "" : hrefMatcher.group(2).trim();
            if (href.isBlank()) {
                continue;
            }
            Optional<String> imgUrl = parseQueryParameter(href, "imgurl");
            sources.add(imgUrl.orElse(href));
        }

        return new ArrayList<>(sources);
    }


    public HtmlImportResult scrapeGoogleImages(String folderPath, String query, int maxImages) throws IOException {
        return scrapeGoogleImages(folderPath, query, maxImages, DEFAULT_GOOGLE_SCRAP_PARALLELISM);
    }

    public HtmlImportResult scrapeGoogleImages(String folderPath, String query, int maxImages, int parallelism) throws IOException {
        Path folder = resolveSafePath(folderPath);
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Not a directory: " + folderPath);
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Google query is required");
        }

        int safeMax = Math.max(1, Math.min(maxImages <= 0 ? 20 : maxImages, 100));
        int safeParallelism = sanitizeParallelism(parallelism);
        String url = "https://www.google.com/search?udm=2&q=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Google request interrupted", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("Google search failed: HTTP " + response.statusCode());
        }

        String html = response.body() == null ? "" : response.body();
        List<String> files = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        List<String> sourcePageUrls = extractGoogleSourcePageUrls(html);
        importFromSourcePagesConcurrently(folder, safeMax, safeParallelism, sourcePageUrls, seenUrls, files);

        if (files.isEmpty()) {
            Matcher matcher = GOOGLE_IMAGE_URL_PATTERN.matcher(html);
            while (matcher.find() && files.size() < safeMax) {
                String candidate = matcher.group();
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                if (!looksLikeImageUrl(candidate)) {
                    continue;
                }
                if (!seenUrls.add(candidate)) {
                    continue;
                }

                try {
                    ImportedImage imported = loadImageFromSrc(candidate);
                    Path target = findAvailableName(folder, imported.baseName(), "." + imported.extension());
                    Files.write(target, imported.bytes());
                    files.add(target.getFileName().toString());
                } catch (Exception ignored) {
                    // continue on invalid or blocked source
                }
            }
        }

        return new HtmlImportResult(files.size(), files);
    }

    int sanitizeParallelism(int parallelism) {
        if (parallelism <= 0) {
            return DEFAULT_GOOGLE_SCRAP_PARALLELISM;
        }
        return Math.min(parallelism, MAX_GOOGLE_SCRAP_PARALLELISM);
    }

    private void importFromSourcePagesConcurrently(Path folder,
                                                   int safeMax,
                                                   int safeParallelism,
                                                   List<String> sourcePageUrls,
                                                   Set<String> seenUrls,
                                                   List<String> files) {
        if (sourcePageUrls.isEmpty()) {
            return;
        }

        List<String> pendingCandidates = new ArrayList<>();
        List<Future<List<String>>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(safeParallelism)) {
            for (String sourcePageUrl : sourcePageUrls) {
                futures.add(executor.submit(() -> extractImageUrlsFromWebPage(sourcePageUrl)));
            }

            for (Future<List<String>> future : futures) {
                if (files.size() >= safeMax) {
                    break;
                }
                try {
                    List<String> candidates = future.get();
                    for (String candidate : candidates) {
                        if (looksLikeImageUrl(candidate) && seenUrls.add(candidate)) {
                            pendingCandidates.add(candidate);
                        }
                    }
                } catch (Exception ignored) {
                    // continue on blocked/unreachable source page
                }
            }

            List<Future<ImportedImageFile>> downloadFutures = new ArrayList<>();
            for (String candidate : pendingCandidates) {
                downloadFutures.add(executor.submit(() -> downloadImportedImageFile(candidate)));
            }

            for (Future<ImportedImageFile> future : downloadFutures) {
                if (files.size() >= safeMax) {
                    break;
                }
                try {
                    ImportedImageFile importedFile = future.get();
                    if (importedFile == null) {
                        continue;
                    }
                    synchronized (files) {
                        if (files.size() >= safeMax) {
                            continue;
                        }
                        Path target = findAvailableName(folder, importedFile.importedImage().baseName(), "." + importedFile.importedImage().extension());
                        Files.write(target, importedFile.importedImage().bytes());
                        files.add(target.getFileName().toString());
                    }
                } catch (Exception ignored) {
                    // continue on invalid or blocked source
                }
            }
        }
    }

    private ImportedImageFile downloadImportedImageFile(String candidate) {
        try {
            ImportedImage imported = loadImageFromSrc(candidate);
            return new ImportedImageFile(candidate, imported);
        } catch (Exception ignored) {
            return null;
        }
    }

    List<String> extractGoogleSourcePageUrls(String html) {
        Set<String> urls = new java.util.LinkedHashSet<>();
        Matcher matcher = GOOGLE_RESULT_LINK_PATTERN.matcher(html == null ? "" : html);
        while (matcher.find()) {
            String href = matcher.group();
            if (href == null || href.isBlank()) {
                continue;
            }
            String absolute = href.startsWith("/") ? "https://www.google.com" + href : href;
            parseQueryParameter(absolute, "imgrefurl").ifPresent(urls::add);
        }
        return new ArrayList<>(urls);
    }

    List<String> extractImageUrlsFromWebPage(String pageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(pageUrl))
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }

            String html = response.body() == null ? "" : response.body();
            Set<String> result = new java.util.LinkedHashSet<>();

            Matcher metaMatcher = META_CONTENT_PATTERN.matcher(html);
            while (metaMatcher.find()) {
                String src = metaMatcher.group(3);
                resolveAbsoluteUrl(pageUrl, src).ifPresent(result::add);
            }

            Matcher imgMatcher = IMG_SRC_PATTERN.matcher(html);
            while (imgMatcher.find() && result.size() < 20) {
                String src = imgMatcher.group(2);
                resolveAbsoluteUrl(pageUrl, src).ifPresent(result::add);
            }

            return new ArrayList<>(result);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Optional<String> resolveAbsoluteUrl(String pageUrl, String src) {
        if (src == null || src.isBlank()) {
            return Optional.empty();
        }
        try {
            URI base = URI.create(pageUrl);
            URI resolved = base.resolve(src.trim());
            if (!"http".equalsIgnoreCase(resolved.getScheme()) && !"https".equalsIgnoreCase(resolved.getScheme())) {
                return Optional.empty();
            }
            return Optional.of(resolved.toString());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<String> parseQueryParameter(String url, String key) {
        try {
            URI uri = URI.create(url);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return Optional.empty();
            }
            for (String pair : query.split("&")) {
                int idx = pair.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String param = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                if (!key.equals(param)) {
                    continue;
                }
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                if (value.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(value);
            }
            return Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private boolean looksLikeImageUrl(String candidate) {
        if (candidate.contains("gstatic.com/images") || candidate.contains("googleusercontent.com")) {
            return true;
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        return lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") || lower.contains(".webp") || lower.contains(".gif");
    }

    public ImportedSingleImage importImageFromClipboard(String folderPath, String imageBase64, String mimeType) throws IOException {
        Path folder = resolveSafePath(folderPath);
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Not a directory: " + folderPath);
        }
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new IllegalArgumentException("imageBase64 is required");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(imageBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid base64 image payload");
        }

        if (bytes.length > MAX_CLIPBOARD_IMAGE_BYTES) {
            throw new IllegalArgumentException("Clipboard image too large (max 10 Mo)");
        }

        String extension = detectExtension(bytes).orElseGet(() -> extensionFromMime(mimeType).orElseThrow(() -> new IllegalArgumentException("Unsupported clipboard image format")));
        int nextNumber = firstFreeNumericBasename(folder);
        String filename = formatNumericName(nextNumber) + "." + extension;
        Path output = folder.resolve(filename);
        Files.write(output, bytes);
        return new ImportedSingleImage(output.toString(), filename);
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

    private ImportedImage loadImageFromSrc(String src) throws Exception {
        if (src.regionMatches(true, 0, "data:", 0, 5)) {
            return parseDataUrl(src);
        }
        URI uri = URI.create(src);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Unsupported src scheme");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", BROWSER_USER_AGENT)
                .GET()
                .build();
        HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("Download failed: HTTP " + response.statusCode());
        }
        byte[] bytes = response.body();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Downloaded image is empty");
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String extension = detectExtension(bytes)
                .or(() -> extensionFromMime(contentType))
                .or(() -> extensionFromPath(uri.getPath()))
                .orElseThrow(() -> new IllegalArgumentException("Unsupported image format"));
        String baseName = sanitizedBaseNameFromUri(uri);
        return new ImportedImage(bytes, extension, baseName);
    }

    private ImportedImage parseDataUrl(String src) {
        int comma = src.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("Invalid data URL");
        }
        String metadata = src.substring(5, comma);
        String payload = src.substring(comma + 1);
        if (payload.isBlank()) {
            throw new IllegalArgumentException("Empty data URL payload");
        }
        if (!metadata.toLowerCase(Locale.ROOT).contains(";base64")) {
            throw new IllegalArgumentException("Only base64 data URL is supported");
        }

        String mimeType = metadata.split(";", 2)[0];
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64 data URL payload");
        }
        String extension = detectExtension(bytes)
                .or(() -> extensionFromMime(mimeType))
                .orElseThrow(() -> new IllegalArgumentException("Unsupported image format in data URL"));
        return new ImportedImage(bytes, extension, "clipboard");
    }

    private Optional<String> detectExtension(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return Optional.empty();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                return normalizeFormat(format);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return Optional.empty();
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "jpg", "jpeg" -> Optional.of("jpg");
            case "png" -> Optional.of("png");
            case "gif" -> Optional.of("gif");
            case "webp" -> Optional.of("webp");
            default -> Optional.empty();
        };
    }

    private Optional<String> extensionFromMime(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return Optional.empty();
        }
        String normalized = mimeType.toLowerCase(Locale.ROOT).trim();
        int separator = normalized.indexOf(';');
        if (separator >= 0) {
            normalized = normalized.substring(0, separator).trim();
        }
        return switch (normalized) {
            case "image/jpeg", "image/jpg" -> Optional.of("jpg");
            case "image/png" -> Optional.of("png");
            case "image/gif" -> Optional.of("gif");
            case "image/webp" -> Optional.of("webp");
            default -> Optional.empty();
        };
    }

    private Optional<String> extensionFromPath(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
        int slash = decoded.lastIndexOf('/');
        String filename = slash >= 0 ? decoded.substring(slash + 1) : decoded;
        String ext = extensionOf(filename).replace(".", "");
        if (SUPPORTED_EXTENSIONS.contains(ext)) {
            return Optional.of(ext);
        }
        return Optional.empty();
    }

    private String sanitizedBaseNameFromUri(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return "imported";
        }
        String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
        int slash = decoded.lastIndexOf('/');
        String filename = slash >= 0 ? decoded.substring(slash + 1) : decoded;
        String base = baseNameWithoutExtension(filename).replaceAll("[^a-zA-Z0-9._-]", "-");
        if (base.isBlank()) {
            return "imported";
        }
        if (base.length() > 64) {
            return base.substring(0, 64);
        }
        return base;
    }

    private int firstFreeNumericBasename(Path folder) throws IOException {
        Set<Integer> used = new HashSet<>();
        try (Stream<Path> stream = Files.list(folder)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString();
                String base = baseNameWithoutExtension(name);
                if (base.matches("\\d+")) {
                    try {
                        used.add(Integer.parseInt(base));
                    } catch (NumberFormatException ignored) {
                        // ignore huge values
                    }
                }
            });
        }
        int candidate = 1;
        while (used.contains(candidate)) {
            candidate++;
        }
        return candidate;
    }

    private String formatNumericName(int value) {
        return value < 100 ? String.format("%02d", value) : Integer.toString(value);
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

    private static int countImagesInFolder(Path folderPath) {
        try (Stream<Path> files = Files.list(folderPath)) {
            return (int) files.filter(Files::isRegularFile).filter(PhotoService::isImage).count();
        } catch (IOException exception) {
            return 0;
        }
    }

    public record FolderEntries(String currentPath, List<ImageEntry> images, List<FolderEntry> folders) {
    }

    public record ImageEntry(String path, String name, long modifiedAt, int width, int height, String extension, long sizeBytes) {
    }

    public record FolderEntry(String path, String name, long modifiedAt, int imageCount) {
    }

    public record KeepResult(String path, String filename) {
    }

    public record HtmlImportResult(int importedCount, List<String> files) {
    }

    public record ImportedSingleImage(String path, String filename) {
    }

    private record ImportedImage(byte[] bytes, String extension, String baseName) {
    }

    private record ImportedImageFile(String sourceUrl, ImportedImage importedImage) {
    }

    private record ImageSize(int width, int height) {
    }
}
