package org.exist.maven.plugins.publicxarrepo;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Tests that {@link ResolveMojo} falls back to the local cache when the
 * remote repo is unreachable, and only fails the build when the cache
 * cannot satisfy the request either.
 */
public class ResolveMojoCacheFallbackTest extends AbstractMojoTestCase {

    /**
     * A URI that reliably refuses connections (port 1 is never listening),
     * simulating a remote repo outage.
     */
    private static final String UNREACHABLE_REPO_URI = "http://127.0.0.1:1";

    @Override
    protected void setUp() throws Exception {
        // required for mojo lookups to work
        getContainer().getContext().put("project.groupId", "blah");
        super.setUp();
    }

    public void testFallsBackToCacheWhenRepoUnreachable() throws Exception {
        final Path tempDir = Files.createTempDirectory("public-xar-repo-plugin-test");
        final Path cacheDir = Files.createDirectories(tempDir.resolve("cache"));
        final Path outputDir = tempDir.resolve("output");

        final ResolveMojo mojo = lookupConfiguredMojo(cacheDir, outputDir);
        final Package pkg = newPackage("dashboard");
        setVariableValueToObject(mojo, "packages", Collections.singletonList(pkg));

        // seed the cache with a previously downloaded package
        final Path xar = tempDir.resolve("dashboard-1.0.0.xar");
        Files.write(xar, "fake-xar-content".getBytes(StandardCharsets.UTF_8));
        final PackageInfo pkgInfo = new PackageInfo(FileUtils.sha256(xar), "1.0.0", "dashboard-1.0.0.xar");
        new CacheManager(cacheDir, mojo.getLog()).put(pkg, pkgInfo, xar);

        // must resolve from the cache instead of failing the build
        mojo.execute();

        assertTrue("cached package should have been copied to the output directory",
                Files.exists(outputDir.resolve("dashboard-1.0.0.xar")));
    }

    public void testFailsWhenRepoUnreachableAndCacheEmpty() throws Exception {
        final Path tempDir = Files.createTempDirectory("public-xar-repo-plugin-test");
        final Path cacheDir = Files.createDirectories(tempDir.resolve("cache"));
        final Path outputDir = tempDir.resolve("output");

        final ResolveMojo mojo = lookupConfiguredMojo(cacheDir, outputDir);
        setVariableValueToObject(mojo, "packages", Collections.singletonList(newPackage("dashboard")));

        try {
            mojo.execute();
            fail("expected MojoFailureException when the repo is unreachable and the cache is empty");
        } catch (final MojoFailureException e) {
            assertTrue("failure message should explain the repo is unreachable: " + e.getMessage(),
                    e.getMessage().contains("unreachable"));
        }
    }

    private ResolveMojo lookupConfiguredMojo(final Path cacheDir, final Path outputDir) throws Exception {
        final File testPom = new File(getBasedir(), "src/test/resources/unit/resolve-basic-test/pom.xml");
        final ResolveMojo mojo = (ResolveMojo) lookupMojo("resolve", testPom);
        setVariableValueToObject(mojo, "repoUri", UNREACHABLE_REPO_URI);
        setVariableValueToObject(mojo, "existDbVersion", "6.0.0");
        setVariableValueToObject(mojo, "cache", Boolean.TRUE);
        setVariableValueToObject(mojo, "cacheDirectory", cacheDir.toFile());
        setVariableValueToObject(mojo, "outputDirectory", outputDir.toFile());
        return mojo;
    }

    private Package newPackage(final String abbrev) throws Exception {
        final Package pkg = new Package();
        setVariableValueToObject(pkg, "abbrev", abbrev);
        return pkg;
    }
}
