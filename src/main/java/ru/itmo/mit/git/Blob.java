package ru.itmo.mit.git;

import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Blob implements GitObject {
    private final String fileName;
    private String hash;
    private final byte[] content;

    public Blob(@NotNull Path root, @NotNull byte[] content, @NotNull String fileName) {
        this.content = content;
        this.fileName = fileName;
        updateHash();
        try {
            GitObject.write(this, root);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public Type getType() {
        return Type.BLOB;
    }

    @Override
    public String getHash() {
        return hash;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getContent() {
        return content;
    }

    private void updateHash() {
        byte[] array = new byte[content.length + fileName.getBytes().length];
        System.arraycopy(content, 0, array, 0, content.length);
        System.arraycopy(fileName.getBytes(), 0, array, content.length, fileName.getBytes().length);
        hash = DigestUtils.sha1Hex(array);
    }

    public static @NotNull String getFileHash(Path path) throws IOException {
        byte[] content = Files.readAllBytes(path);
        String fileName = path.getFileName().toString();
        byte[] array = new byte[content.length + fileName.getBytes().length];
        System.arraycopy(content, 0, array, 0, content.length);
        System.arraycopy(fileName.getBytes(), 0, array, content.length, fileName.getBytes().length);
        return  DigestUtils.sha1Hex(array);
    }

    public static boolean exist(String hash, Path blobDir) {
        Path blobFile = blobDir.resolve(hash);
        return Files.exists(blobFile);
    }
}
