
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest

import static org.junit.Assert.*

final String GROUP_ID = "org.expath.pkg.it"
final String ARTIFACT_ID = "xquery-library"
final String VERSION = "1.0.0"


final Path target = basedir.toPath().resolve("target")
if (!Files.exists(target) || !Files.isDirectory(target)) {
    System.err.println("target directory is missing.")
    return false
}

final Path xarOutputDir = target.resolve("xars")

final Path functxXar = xarOutputDir.resolve("functx-1.0.xar")
if (!Files.exists(functxXar)) {
    System.err.println("xar file is missing.")
    return false
}

functxXar.withInputStream() { actualIs ->
    assertEquals("da85074ce5edbeceeaa56c431b7f551e6862dfd83b901670072b6b683266e74f", checksum(actualIs))
}

def checksum(final InputStream is) {
    MessageDigest md = MessageDigest.getInstance("SHA-256")
    new DigestInputStream(is, md).with { dis ->
        while (dis.read() != -1) { }

        md = dis.getMessageDigest()

        // bytes to hex
        StringBuilder result = new StringBuilder()
        for (byte b : md.digest()) {
            result.append(String.format("%02x", b))
        }
        return result.toString()
    }
}