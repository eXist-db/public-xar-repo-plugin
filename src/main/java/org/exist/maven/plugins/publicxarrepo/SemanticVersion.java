package org.exist.maven.plugins.publicxarrepo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SemanticVersion implements Comparable<SemanticVersion> {
    private static final Pattern PTN_SEMVER = Pattern.compile("([0-9]+)(?:\\.([0-9]+))?(?:\\.([0-9]+))?");

    private final int major;
    private final int minor;
    private final int patch;

    public SemanticVersion(final int major, final int minor, final int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static SemanticVersion parse(final String version) throws IllegalArgumentException {
        final Matcher matcher = PTN_SEMVER.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid semver: " + version);
        }

        final int major = Integer.parseInt(matcher.group(1));
        final int minor;
        if (matcher.groupCount() > 1 && matcher.group(2) != null) {
            minor = Integer.parseInt(matcher.group(2));
        } else {
            minor = 0;
        }
        final int patch;
        if (matcher.groupCount() > 2 && matcher.group(3) != null) {
            patch = Integer.parseInt(matcher.group(3));
        } else {
            patch = 0;
        }

        return new SemanticVersion(major, minor, patch);
    }

    @Override
    public int compareTo(final SemanticVersion other) {
        if (major - other.major != 0) {
            return major - other.major;
        }

        if (minor - other.minor != 0) {
            return minor - other.minor;
        }

        return patch - other.patch;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
