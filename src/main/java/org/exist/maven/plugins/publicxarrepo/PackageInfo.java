package org.exist.maven.plugins.publicxarrepo;

public class PackageInfo {

    private final String sha256;
    private final String version;
    private final String path;

    public PackageInfo(final String sha256, final String version, final String path) {
        this.sha256 = sha256;
        this.version = version;
        this.path = path;
    }

    public String getSha256() {
        return sha256;
    }

    public String getVersion() {
        return version;
    }

    public String getPath() {
        return path;
    }
}
