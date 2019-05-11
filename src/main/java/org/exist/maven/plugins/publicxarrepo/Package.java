package org.exist.maven.plugins.publicxarrepo;

import org.apache.maven.plugins.annotations.Parameter;

public class Package {

    @Parameter
    private String name;

    @Parameter
    private String abbrev;

    @Parameter
    private String version;

    @Parameter
    private String semanticVersion;

    @Parameter
    private String semanticVersionMin;

    @Parameter
    private String semanticVersionMax;

    public String getName() {
        return name;
    }

    public String getAbbrev() {
        return abbrev;
    }

    public String getVersion() {
        return version;
    }

    public String getSemanticVersion() {
        return semanticVersion;
    }

    public String getSemanticVersionMin() {
        return semanticVersionMin;
    }

    public String getSemanticVersionMax() {
        return semanticVersionMax;
    }
}
