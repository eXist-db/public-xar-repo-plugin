package org.exist.maven.plugins.publicxarrepo;

import com.evolvedbinary.j8fu.lazy.LazyVal;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

import static org.apache.http.HttpStatus.SC_OK;
import static org.exist.maven.plugins.publicxarrepo.XmlUtils.DOCUMENT_BUILDER_FACTORY;

@Mojo(name = "resolve", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true, requiresProject = false)
public class ResolveMojo extends AbstractMojo {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int SOCKET_TIMEOUT_MS = 30_000;
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 2_000;

    @Parameter(required = true, defaultValue = "http://exist-db.org/exist/apps/public-repo")
    private String repoUri;

    @Parameter(required = true)
    private String existDbVersion;

    @Parameter(required = true, defaultValue = "true")
    private boolean cache;

    @Parameter(required = true, defaultValue = "${project.build.directory}/xars")
    private File outputDirectory;

    /**
     * The directory to use as a cache. Default is
     * ${local-repo}/.cache/maven-download-plugin
     */
    @Parameter
    private File cacheDirectory;

    @Parameter(required = true)
    private List<Package> packages;

    @Parameter(property = "xar.resolve.offline", defaultValue = "false")
    public boolean offline;

    @Parameter(property = "session")
    private MavenSession session;

    @Component(role = SettingsDecrypter.class)
    private SettingsDecrypter decrypter;

    private final PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager();

    private final LazyVal<List<Proxy>> proxies = new LazyVal<>(() -> MojoUtils.getProxies(session, decrypter));


    public ResolveMojo() {
        poolingHttpClientConnectionManager.setDefaultMaxPerRoute(15);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        for (final Package pkg : packages) {
            if ((pkg.getName() == null || pkg.getName().isEmpty())
                    && (pkg.getAbbrev() == null || pkg.getAbbrev().isEmpty())) {
                throw new MojoFailureException("Each configured package must have a `name` or `abbrev`");
            }

            getLog().info("Attempting to resolve package: " + pkg.toString());

            resolvePackage(pkg);
        }
    }

    private void resolvePackage(final Package pkg) throws MojoExecutionException, MojoFailureException {
        final Path outputDirectoryPath = outputDirectory.toPath();
        try {
            final CacheManager cacheManager = cache ? newCacheManager() : null;
            final boolean isOffline = offline || (session != null && session.isOffline());

            /* If the remote repo cannot be reached, fall back to the cache (if enabled)
               rather than failing the build: the cache may still hold a usable version. */
            final PackageInfo pkgInfo = isOffline ? null : getPackageInfoOrNull(pkg);

            if (serveFromCache(cacheManager, pkg, pkgInfo, outputDirectoryPath, isOffline)) {
                return;
            }

            if (isOffline) {
                throw new MojoFailureException("Cannot resolve packages from remote when in offline mode.");
            }

            if (pkgInfo == null) {
                throw new MojoFailureException("Unable to resolve package " + pkg + ": the remote repo is unreachable and no suitable cached copy is available.");
            }

            downloadAndStore(cacheManager, pkg, pkgInfo, outputDirectoryPath);
        } catch (final IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * Download the package from the remote repo, validate its checksum, move it
     * into the output directory, and record it in the cache (if enabled).
     *
     * @param cacheManager the cache manager, or null if caching is disabled.
     * @param pkg the requested package.
     * @param pkgInfo the info about the package to download.
     * @param outputDirectoryPath the directory to move the package to.
     */
    private void downloadAndStore(@Nullable final CacheManager cacheManager, final Package pkg,
            final PackageInfo pkgInfo, final Path outputDirectoryPath)
            throws MojoExecutionException, MojoFailureException, IOException {
        Path path = downloadPackage(pkgInfo);

        // validate the checksum of the downloaded file
        final String pathChecksum = FileUtils.sha256(path);
        if (!pkgInfo.getSha256().equals(pathChecksum)) {
            throw new MojoFailureException("Downloaded file does not match PackageInfo checksum: expected=" + pkgInfo.getSha256() + ", actual=" + pathChecksum);
        }

        if (!Files.exists(outputDirectoryPath)) {
            Files.createDirectories(outputDirectoryPath);
        }
        path = moveFile(path, outputDirectoryPath.resolve(pkgInfo.getPath()));
        getLog().info("Resolved package from server: " + path.getFileName());

        if (cacheManager != null) {
            cacheManager.put(pkg, pkgInfo, path);
        }
    }

    private CacheManager newCacheManager() throws IOException {
        final Path cacheDir = Optional.ofNullable(cacheDirectory).map(File::toPath)
                .orElseGet(() -> Paths.get(this.session.getLocalRepository().getBasedir()).resolve(".cache").resolve("public-xar-repo-plugin"));
        getLog().debug("Cache is: " + cacheDir.toAbsolutePath().toString());
        Files.createDirectories(cacheDir);
        return new CacheManager(cacheDir, getLog());
    }

    /**
     * Retrieve the package info from the remote repo, or return null if the
     * repo cannot be reached, so that the caller can fall back to the cache.
     *
     * @param pkg the package to retrieve info for.
     *
     * @return the package info, or null if the remote repo is unreachable.
     */
    private @Nullable PackageInfo getPackageInfoOrNull(final Package pkg) {
        try {
            return getPackageInfo(pkg);
        } catch (final MojoExecutionException e) {
            getLog().warn("Unable to retrieve package info from remote repo: " + e.getMessage() + ". Falling back to the local cache...");
            return null;
        }
    }

    /**
     * Attempt to satisfy the package request from the cache.
     *
     * @param cacheManager the cache manager, or null if caching is disabled.
     * @param pkg the requested package.
     * @param pkgInfo the latest info about the package, or null if not available.
     * @param outputDirectoryPath the directory to copy the package to.
     * @param isOffline true if operating in offline mode.
     *
     * @return true if the package was served from the cache.
     */
    private boolean serveFromCache(@Nullable final CacheManager cacheManager, final Package pkg,
            @Nullable final PackageInfo pkgInfo, final Path outputDirectoryPath, final boolean isOffline)
            throws IOException {
        final Path path = cacheManager != null ? cacheManager.get(pkg, pkgInfo) : null;
        if (path == null) {
            return false;
        }

        if (!Files.exists(outputDirectoryPath)) {
            Files.createDirectories(outputDirectoryPath);
        }
        Files.copy(path, outputDirectoryPath.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        if (pkgInfo == null) {
            if (isOffline) {
                getLog().warn("ResolveMojo is operating in offline mode, so package version could not be checked with remote repo!");
            } else {
                getLog().warn("Could not check version with remote repo, no remote info available!");
            }
        }
        getLog().info("Resolved package from cache: " + path.getFileName());
        return true;
    }

    private Path moveFile(Path source, Path target) throws IOException {
        try {
            return Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notSupported) {
            getLog().debug("Atomic move from: " + source + " to " + target + " failed. Retrying with non-atomic move...");
            return Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private CloseableHttpClient buildHttpClient(@Nullable final Proxy proxy) {
        final HttpClientBuilder clientBuilder = HttpClients
                .custom()
                .setConnectionManager(poolingHttpClientConnectionManager);

        if (proxy != null && proxy.getUsername() != null && !proxy.getUsername().isEmpty()) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(proxy.getHost(), proxy.getPort()),
                    new UsernamePasswordCredentials(proxy.getUsername(), proxy.getPassword())
            );
            clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }

        return clientBuilder.build();
    }

    private Request buildGetRequest(@Nullable final Proxy proxy, final String uri) {
        Request request = Request.Get(uri)
                .connectTimeout(CONNECT_TIMEOUT_MS)
                .socketTimeout(SOCKET_TIMEOUT_MS);
        if (proxy != null) {
            final HttpHost proxyHttpHost = new HttpHost(proxy.getHost(), proxy.getPort());
            request = request.viaProxy(proxyHttpHost);
        }
        return request;
    }

    /**
     * Execute a GET request, retrying on I/O errors and HTTP 5xx server errors
     * with exponential backoff. Non-5xx responses (including 404) are considered
     * definitive and returned to the caller without retrying.
     *
     * @param executor the HTTP executor to use.
     * @param proxy the proxy to route the request via, or null.
     * @param uri the URI to GET.
     *
     * @return the HTTP response.
     *
     * @throws IOException if all attempts fail with an I/O error or server error.
     */
    private HttpResponse executeWithRetry(final Executor executor, @Nullable final Proxy proxy, final String uri) throws IOException {
        IOException lastIoException = null;
        String lastError = null;
        long delay = INITIAL_RETRY_DELAY_MS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                final HttpResponse response = executor.execute(buildGetRequest(proxy, uri)).returnResponse();
                final int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode < 500) {
                    return response;
                }
                lastIoException = null;
                lastError = "HTTP " + statusCode;
            } catch (final IOException e) {
                lastIoException = e;
                lastError = e.getMessage();
            }
            if (attempt < MAX_ATTEMPTS) {
                getLog().warn("Attempt " + attempt + " of " + MAX_ATTEMPTS + " failed for " + uri + " (" + lastError + "), retrying in " + (delay / 1000) + "s...");
                try {
                    Thread.sleep(delay);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting to retry: " + uri, e);
                }
                delay *= 2;
            }
        }
        if (lastIoException != null) {
            throw lastIoException;
        }
        throw new IOException("Received " + lastError + " from " + uri + " after " + MAX_ATTEMPTS + " attempts");
    }

    private PackageInfo getPackageInfo(final Package pkg) throws MojoExecutionException {
        getLog().info("Retrieving package info for " + (pkg.getName() != null ? pkg.getName() : pkg.getAbbrev()));
        try {
            final String uri = getFindUri(pkg) + "&info=true";
            @Nullable final Proxy proxy = MojoUtils.getProxyForUrl(proxies.get(), uri);

            final CloseableHttpClient client = buildHttpClient(proxy);
            final Executor executor = Executor.newInstance(client);

            final HttpResponse response = executeWithRetry(executor, proxy, uri);
            if (response.getStatusLine().getStatusCode() != SC_OK) {
                getLog().error("Received HTTP " + response.getStatusLine().getStatusCode() + " when trying to access: " + uri);
                throw new MojoExecutionException("Unable to get package info");
            }

            final DocumentBuilder builder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
            try (final InputStream is = response.getEntity().getContent()) {
                final Document document = builder.parse(is);
                final Element root = document.getDocumentElement();
                if (root == null || !root.getLocalName().equals("found")) {
                    throw new MojoExecutionException("Received package info is invalid");
                }

                return new PackageInfo(root.getAttribute("sha256"), root.getAttribute("version"), root.getAttribute("path"));
            }
        } catch (final IOException | ParserConfigurationException | SAXException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private String getFindUri(final Package pkg) {
        final StringBuilder builder = new StringBuilder();
        builder.append(getRepoUri());
        builder.append("/find?processor=");
        builder.append(existDbVersion);
        if (pkg.getName() != null) {
            builder.append("&name=");
            builder.append(pkg.getName());
        }
        if (pkg.getAbbrev() != null) {
            builder.append("&abbrev=");
            builder.append(pkg.getAbbrev());
        }
        if (pkg.getVersion() != null) {
            builder.append("&version=");
            builder.append(pkg.getVersion());
        }
        if (pkg.getSemanticVersion() != null) {
            builder.append("&semver=");
            builder.append(pkg.getSemanticVersion());
        }
        if (pkg.getSemanticVersionMin() != null) {
            builder.append("&semver-min=");
            builder.append(pkg.getSemanticVersionMin());
        }
        if (pkg.getSemanticVersionMax() != null) {
            builder.append("&semver-max=");
            builder.append(pkg.getSemanticVersionMax());
        }
        return builder.toString();
    }

    private Path downloadPackage(final PackageInfo pkgInfo) throws MojoExecutionException {
        try {
            final String uri = getPackageUri(pkgInfo);
            @Nullable final Proxy proxy = MojoUtils.getProxyForUrl(proxies.get(), uri);

            final CloseableHttpClient client = buildHttpClient(proxy);
            final Executor executor = Executor.newInstance(client);

            getLog().info("Downloading " + uri);
            final HttpResponse response = executeWithRetry(executor, proxy, uri);
            if (response.getStatusLine().getStatusCode() != SC_OK) {
                getLog().error("Received HTTP " + response.getStatusLine().getStatusCode() + " when trying to access: " + uri);
                throw new MojoExecutionException("Unable to download package: " + pkgInfo.getPath());
            }

            final Path tmpFile = Files.createTempFile(pkgInfo.getPath(), ".tmp");
            try (final InputStream is = response.getEntity().getContent()) {
                Files.copy(is, tmpFile, StandardCopyOption.REPLACE_EXISTING);
                return tmpFile;
            }
        } catch (final IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    protected String getPackageUri(final PackageInfo pkgInfo) {
        final StringBuilder builder = new StringBuilder();
        builder.append(getRepoUri());
        builder.append("/public/");
        builder.append(pkgInfo.getPath());
        return builder.toString();
    }

    protected String getRepoUri() {
        return repoUri;
    }
}
