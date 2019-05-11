package org.exist.maven.plugins.publicxarrepo;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileUtils {

    public static String sha256(final Path path) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new IOException(e.getMessage(), e);
        }

        try (final InputStream is = new BufferedInputStream(Files.newInputStream(path))) {
            final byte[] buf = new byte[1024];
            int read = -1;
            while ((read = is.read(buf)) > -1) {
                digest.update(buf, 0, read);
            }
        }

        final byte[] hash = digest.digest();
        final StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if(hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }

        return hexString.toString();
    }
}
