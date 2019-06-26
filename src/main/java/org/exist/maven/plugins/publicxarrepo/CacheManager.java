package org.exist.maven.plugins.publicxarrepo;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.maven.plugin.logging.Log;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.maven.plugins.publicxarrepo.FileUtils.sha256;
import static org.exist.maven.plugins.publicxarrepo.PackageInfo.METADATA_FILE_EXTENSION;

public class CacheManager {
    private final Path dir;
    private final Log log;

    public CacheManager(final Path dir, final Log log) {
        this.dir = dir;
        this.log = log;
    }

    /**
     * Get the path of a package from the cache.
     *
     * @param pkg the package to retrieve from the cache.
     * @param pkgInfo the latest info about the package, or null if not available.
     *
     * @return the path of the package in the cache, or null if not available
     *
     */
    public @Nullable Path get(final Package pkg, final @Nullable PackageInfo pkgInfo) throws IOException {
        final Tuple2<SemanticVersion, Path> cachedVersion = getVersionFromCache(pkg);
        if (cachedVersion == null) {
            return null;
        }

        if (pkgInfo != null) {
            final SemanticVersion pkgInfoVersion = SemanticVersion.parse(pkgInfo.getVersion());

            if (cachedVersion._1.compareTo(pkgInfoVersion) != 0) {
                // the version from the cache is not the latest/correct
                return null;
            }

            //
            if (!sha256(cachedVersion._2).equals(pkgInfo.getSha256())) {
                // sha256 does not match!
                log.warn("SHA-256 checksum of " + cachedVersion._2.getFileName() + " does not match remote server version, cached version will be refreshed...");
                return null;
            }
        }

        return cachedVersion._2;
    }

    private @Nullable Tuple2<SemanticVersion, Path> getVersionFromCache(final Package pkg) throws IOException {
        final List<PackageInfo> cachedPackageInfos = PackageDb.findPackageInfos(dir, getAbbrevAndOrName(pkg));
        if (cachedPackageInfos.isEmpty()) {
            return null;
        }

        final SemanticVersion pkgVersion = pkg.getVersion() != null ? SemanticVersion.parse(pkg.getVersion()) : null;
        final SemanticVersion pkgSemanticVersion = pkg.getSemanticVersion() != null ? SemanticVersion.parse(pkg.getSemanticVersion()) : null;
        final SemanticVersion pkgSemanticVersionMin = pkg.getSemanticVersionMin() != null ? SemanticVersion.parse(pkg.getSemanticVersionMin()) : null;
        final SemanticVersion pkgSemanticVersionMax = pkg.getSemanticVersionMax() != null ? SemanticVersion.parse(pkg.getSemanticVersionMax()) : null;

        SemanticVersion latestVersion = SemanticVersion.parse("0.0.0");
        Path latestVersionPath = null;

        for (final PackageInfo cachedPackageInfo : cachedPackageInfos) {

            final SemanticVersion filePackageVersion = SemanticVersion.parse(cachedPackageInfo.getVersion());

            /* check the filePackageVersion against pkg, if no version specified in pkg then get the latest */

            if (pkgVersion != null) {
                if (pkgVersion.compareTo(filePackageVersion) == 0) {
                    return Tuple(filePackageVersion, dir.resolve(cachedPackageInfo.getPath()));
                }

            } else if (pkgSemanticVersion != null) {
                if (pkgSemanticVersion.compareTo(filePackageVersion) == 0) {
                    return Tuple(filePackageVersion, dir.resolve(cachedPackageInfo.getPath()));
                }

            } else if (pkgSemanticVersionMin != null) {
                if (pkgSemanticVersionMin.compareTo(filePackageVersion) <= 0 && filePackageVersion.compareTo(latestVersion) > 0) {
                    latestVersion = filePackageVersion;
                    latestVersionPath = dir.resolve(cachedPackageInfo.getPath());
                }

            } else if (pkgSemanticVersionMax != null) {
                if (pkgSemanticVersionMax.compareTo(filePackageVersion) >= 0 && filePackageVersion.compareTo(latestVersion) > 0) {
                    latestVersion = filePackageVersion;
                    latestVersionPath = dir.resolve(cachedPackageInfo.getPath());
                }

            } else {
                if (filePackageVersion.compareTo(latestVersion) > 0) {
                    latestVersion = filePackageVersion;
                    latestVersionPath = dir.resolve(cachedPackageInfo.getPath());
                }
            }
        }

        if (latestVersionPath == null) {
            return null;
        }

        return Tuple(latestVersion, latestVersionPath);
    }

    public void put(final Package pkg, final PackageInfo pkgInfo, final Path path) throws IOException {
        final Path destFile = dir.resolve(path.getFileName());
        try (final FileOutputStream os = new FileOutputStream(destFile.toFile(), false)) {
            final FileLock lock = os.getChannel().lock();
            try {
                Files.copy(path, os);
                serialize(pkgInfo, destFile.resolveSibling(destFile.getFileName().toString() + METADATA_FILE_EXTENSION));
            } finally {
                lock.close();
            }
        }
        PackageDb.addPackageInfo(dir, getAbbrevAndOrName(pkg), pkgInfo);
    }

    private PackageDb.AbbrevAndOrName getAbbrevAndOrName(final Package pkg) {
        final Optional<String> abbrev = Optional.ofNullable(pkg.getAbbrev()).filter(s -> !s.isEmpty());
        final Optional<String> name = Optional.ofNullable(pkg.getAbbrev()).filter(s -> !s.isEmpty());
        return new PackageDb.AbbrevAndOrName(abbrev, name);
    }

    private void serialize(final PackageInfo pkgInfo, final Path path) throws IOException {
        try {
            final StreamResult result = new StreamResult(path.toString());
            pkgInfo.serialize(result);
        } catch (final ParserConfigurationException | TransformerException e) {
            throw new IOException(e);
        }
    }
}
