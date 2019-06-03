package org.exist.maven.plugins.publicxarrepo;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.ArrayList;
import java.util.List;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;

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

    @Override
    public String toString() {
        final List<Tuple2<String, String>> tuples = new ArrayList<>();
        if (getName() != null) {
            tuples.add(Tuple("name", getName()));
        }
        if (getAbbrev() != null) {
            tuples.add(Tuple("abbrev", getAbbrev()));
        }
        if (getVersion() != null) {
            tuples.add(Tuple("version", getVersion()));
        }
        if (getSemanticVersion() != null) {
            tuples.add(Tuple("semantic-version", getSemanticVersion()));
        }
        if (getSemanticVersionMin() != null) {
            tuples.add(Tuple("semantic-version-min", getSemanticVersionMin()));
        }
        if (getSemanticVersionMax() != null) {
            tuples.add(Tuple("semantic-version-max", getSemanticVersionMax()));
        }

        final StringBuilder buf = new StringBuilder();
        buf.append('{');
        for (int i = 0; i < tuples.size(); i++) {
            buf.append('"').append(tuples.get(i)._1).append('"').append(": ")
                    .append('"').append(tuples.get(i)._2).append('"');

            if (i < tuples.size() - 1) {
                buf.append(", ");
            }
        }
        buf.append('}');

        return buf.toString();
    }
}
