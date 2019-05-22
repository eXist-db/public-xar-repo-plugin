package org.exist.maven.plugins.publicxarrepo;

import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser fro Semantic Versioning 2.0.0
 *
 * See https://semver.org/
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class SemanticVersion implements Comparable<SemanticVersion> {
    private static final Pattern PTN_SEMVER = Pattern.compile("([0-9]+)(?:\\.([0-9]+))?(?:\\.([0-9]+))?(?:-([0-9A-Za-z\\-]+(?:\\.[0-9A-Za-z\\-])*))?(?:\\+([0-9A-Za-z\\-]+(?:\\.[0-9A-Za-z\\-])*))?");

    private final int major;
    private final int minor;
    private final int patch;

    @Nullable private final String preReleaseLabel;
    @Nullable private final String buildLabel;

    public SemanticVersion(final int major, final int minor, final int patch) {
        this(major, minor, patch, null, null);
    }

    public SemanticVersion(final int major, final int minor, final int patch, final String preReleaseLabel) {
        this(major, minor, patch, preReleaseLabel, null);
    }

    public SemanticVersion(final int major, final int minor, final int patch,
            @Nullable final String preReleaseLabel, @Nullable final String buildLabel) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preReleaseLabel = preReleaseLabel;
        this.buildLabel = buildLabel;
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
        final String preReleaseLabel;
        if (matcher.groupCount() > 3 && matcher.group(4) != null) {
            preReleaseLabel = matcher.group(4);
        } else {
            preReleaseLabel = null;
        }
        final String buildLabel;
        if (matcher.groupCount() > 4 && matcher.group(5) != null) {
            buildLabel = matcher.group(5);
        } else {
            buildLabel = null;
        }

        return new SemanticVersion(major, minor, patch, preReleaseLabel, buildLabel);
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

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    @Nullable
    public String getPreReleaseLabel() {
        return preReleaseLabel;
    }

    @Nullable
    public String getBuildLabel() {
        return buildLabel;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch +
                (preReleaseLabel != null ? "-" + preReleaseLabel : "") +
                (buildLabel != null ? "+" + buildLabel : "");
    }
}
