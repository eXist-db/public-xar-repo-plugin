package org.exist.maven.plugins.publicxarrepo;

import javax.annotation.Nullable;

public class Utils {
    public static boolean isEmpty(@Nullable final String s) {
        return s == null || s.isEmpty();
    }

    public static boolean isNonEmpty(@Nullable final String s) {
        return s != null && !s.isEmpty();
    }
}
