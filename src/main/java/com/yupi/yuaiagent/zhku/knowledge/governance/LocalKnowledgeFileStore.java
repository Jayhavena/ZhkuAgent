package com.yupi.yuaiagent.zhku.knowledge.governance;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 原始 PDF 的本机受控存储边界。
 *
 * <p>外层只能拿到随机文件标识，绝不把实际路径传给页面、接口或问答端。</p>
 */
public final class LocalKnowledgeFileStore {

    private final Path storageDirectory;

    public LocalKnowledgeFileStore(Path storageDirectory) {
        this.storageDirectory = Objects.requireNonNull(
                storageDirectory,
                "本机资料目录不能为空"
        ).toAbsolutePath().normalize();
    }

    public StoredKnowledgeFile store(
            String originalFilename,
            InputStream content
    ) throws IOException {
        String safeFilename = normalizePdfFilename(originalFilename);
        Objects.requireNonNull(content, "PDF 内容不能为空");
        Path root = prepareStorageRoot();
        String storageKey = UUID.randomUUID() + ".pdf";
        Path temporaryFile = safeResolve(
                root,
                "." + UUID.randomUUID() + ".upload"
        );
        Path targetFile = safeResolve(root, storageKey);

        try {
            CopyResult copyResult = copyAndDigest(content, temporaryFile);
            if (copyResult.size() < 1) {
                throw new IllegalArgumentException("PDF 文件不能为空");
            }
            validateReadablePdf(temporaryFile);
            Files.move(temporaryFile, targetFile);
            return new StoredKnowledgeFile(
                    storageKey,
                    safeFilename,
                    copyResult.sha256(),
                    copyResult.size()
            );
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    /** 仅供同包服务读取受控文件，传入的标识仍需经过路径边界校验。 */
    Path locate(String storageKey) throws IOException {
        Path file = safeResolve(prepareStorageRoot(), storageKey);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("本机 PDF 不存在或已不可读取");
        }
        return file;
    }

    void delete(String storageKey) throws IOException {
        Files.deleteIfExists(safeResolve(prepareStorageRoot(), storageKey));
    }

    private Path prepareStorageRoot() throws IOException {
        Files.createDirectories(storageDirectory);
        return storageDirectory.toRealPath();
    }

    private static String normalizePdfFilename(String originalFilename) {
        String suppliedName = Objects.requireNonNull(
                originalFilename,
                "原始文件名不能为空"
        ).strip();
        if (suppliedName.isEmpty()) {
            throw new IllegalArgumentException("原始文件名不能为空");
        }
        String fileName = Paths.get(suppliedName).getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("仅支持 PDF 文件");
        }
        return fileName;
    }

    private static Path safeResolve(Path root, String fileName) {
        String normalizedKey = Objects.requireNonNull(
                fileName,
                "本机文件标识不能为空"
        );
        if (normalizedKey.contains("/") || normalizedKey.contains("\\")) {
            throw new IllegalArgumentException("本机文件标识无效");
        }
        Path target = root.resolve(normalizedKey).normalize();
        if (!root.equals(target.getParent())) {
            throw new IllegalArgumentException("本机文件标识无效");
        }
        return target;
    }

    private static CopyResult copyAndDigest(
            InputStream content,
            Path target
    ) throws IOException {
        MessageDigest digest = sha256Digest();
        long size = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream output = Files.newOutputStream(target)) {
            for (int read; (read = content.read(buffer)) != -1; ) {
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                size += read;
            }
        }
        return new CopyResult(
                HexFormat.of().formatHex(digest.digest()).toUpperCase(Locale.ROOT),
                size
        );
    }

    private static void validateReadablePdf(Path pdf) {
        try (PDDocument ignored = PDDocument.load(pdf.toFile())) {
            // PDFBox 成功打开即证明当前版本可读取，后续才允许进入暂存区。
        } catch (IOException exception) {
            throw new IllegalArgumentException("PDF 内容无法读取", exception);
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    private record CopyResult(String sha256, long size) {
    }
}
