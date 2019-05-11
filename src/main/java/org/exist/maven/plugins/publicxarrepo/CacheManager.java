package org.exist.maven.plugins.publicxarrepo;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.maven.plugin.logging.Log;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.maven.plugins.publicxarrepo.FileUtils.sha256;

public class CacheManager {
    private static final Pattern PTN_FILENAME = Pattern.compile("([a-zA-Z0-9\\-_]+)-((?:[0-9]+)(?:\\.[0-9]+)?(?:\\.[0-9]+)?)\\.xar");
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
        final List<Path> files;
        try (final Stream<Path> fileStream = Files.list(dir)) {
            files = fileStream.collect(Collectors.toList());
        }

        final SemanticVersion pkgVersion = pkg.getVersion() != null ? SemanticVersion.parse(pkg.getVersion()) : null;
        final SemanticVersion pkgSemanticVersion = pkg.getSemanticVersion() != null ? SemanticVersion.parse(pkg.getSemanticVersion()) : null;
        final SemanticVersion pkgSemanticVersionMin = pkg.getSemanticVersionMin() != null ? SemanticVersion.parse(pkg.getSemanticVersionMin()) : null;
        final SemanticVersion pkgSemanticVersionMax = pkg.getSemanticVersionMax() != null ? SemanticVersion.parse(pkg.getSemanticVersionMax()) : null;

        SemanticVersion latestVersion = SemanticVersion.parse("0.0.0");
        Path latestVersionPath = null;

        for (final Path file : files) {
            final String fileName = file.getFileName().toString();
            final Matcher matcher = PTN_FILENAME.matcher(fileName);
            if (matcher.matches()) {
                final String filePackageName = matcher.group(1);

                //TODO(AR) file is not named based on abbrev or name - we need to look these up in a db
                if (filePackageName.equals(pkg.getAbbrev()) || filePackageName.equals(pkg.getName())) {

                    final SemanticVersion filePackageVersion = SemanticVersion.parse(matcher.group(2));

                    /* check the filePackageVersion against pkg, if no version specified in pkg then get the latest */

                    if (pkgVersion != null) {
                        if (pkgVersion.compareTo(filePackageVersion) == 0) {
                            return Tuple(filePackageVersion, file);
                        }

                    } else if (pkgSemanticVersion != null) {
                        if (pkgSemanticVersion.compareTo(filePackageVersion) == 0) {
                            return Tuple(filePackageVersion, file);
                        }

                    } else if (pkgSemanticVersionMin != null) {
                        if (pkgSemanticVersionMin.compareTo(filePackageVersion) <= 0 && filePackageVersion.compareTo(latestVersion) > 0) {
                            latestVersion = filePackageVersion;
                            latestVersionPath = file;
                        }

                    } else if (pkgSemanticVersionMax != null) {
                        if (pkgSemanticVersionMax.compareTo(filePackageVersion) >= 0 && filePackageVersion.compareTo(latestVersion) > 0) {
                            latestVersion = filePackageVersion;
                            latestVersionPath = file;
                        }

                    } else {
                        if (filePackageVersion.compareTo(latestVersion) > 0) {
                            latestVersion = filePackageVersion;
                            latestVersionPath = file;
                        }
                    }
                }
            }
        }

        if (latestVersionPath == null) {
            return null;
        }

        return Tuple(latestVersion, latestVersionPath);
    }

    public void put(final Path path) throws IOException {
        Files.copy(path, dir.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }
}
