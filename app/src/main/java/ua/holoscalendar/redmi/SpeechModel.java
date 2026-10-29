package ua.holoscalendar.redmi;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only the two upstream multilingual models are accepted before entering native code. */
final class SpeechModel {
    static final long MAX_BYTES = 160L * 1024 * 1024;
    static final String TINY_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin?download=true";
    static final String BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin?download=true";
    // Published by the pinned whisper.cpp models/README.md. These are SHA-1 content pins, not signatures.
    static final String TINY_SHA1 = "bd577a113a864445d4c299885e0cb97d4ba92b5f";
    static final String BASE_SHA1 = "465707469ff3a37a2b9b8d8f89f2f99de7299dac";
    // Reproducible Base conversion used by the device smoke test; see docs/OFFLINE_SPEECH.md.
    static final String CONVERTED_BASE_SHA1 = "faccb500275cbab28076305880ce39de190f01c0";
    private SpeechModel() { }
    static String name(String digest) throws IOException {
        if (TINY_SHA1.equals(digest)) return "Whisper Tiny";
        if (BASE_SHA1.equals(digest) || CONVERTED_BASE_SHA1.equals(digest)) return "Whisper Base";
        throw new IOException("Unsupported or incomplete multilingual model");
    }
    static String copyAndVerify(InputStream input, OutputStream output, AtomicBoolean cancelled) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        byte[] buffer = new byte[65536];
        long size = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new CancellationException();
            size += count;
            if (size > MAX_BYTES) throw new IOException("Model is too large");
            digest.update(buffer, 0, count);
            if (output != null) output.write(buffer, 0, count);
        }
        if (cancelled.get()) throw new CancellationException();
        return name(hex(digest.digest()));
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
    static String verify(File file, AtomicBoolean cancelled) throws IOException {
        if (file.length() > MAX_BYTES) throw new IOException("Model is too large");
        try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            return copyAndVerify(stream, null, cancelled);
        }
    }
    static String install(InputStream input, File destination, AtomicBoolean cancelled) throws IOException {
        File directory = destination.getParentFile();
        if (directory == null || !directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create model folder");
        File partial = File.createTempFile("import-", ".part", directory);
        try {
            String name;
            try (FileOutputStream output = new FileOutputStream(partial)) {
                name = copyAndVerify(input, output, cancelled);
                output.getFD().sync();
            }
            if (cancelled.get()) throw new CancellationException();
            try { Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                if (!partial.renameTo(destination)) throw new IOException("Cannot replace model atomically", unsupported);
            }
            return name;
        } finally { if (partial.exists()) partial.delete(); }
    }
}
