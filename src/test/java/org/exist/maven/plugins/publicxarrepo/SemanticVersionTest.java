package org.exist.maven.plugins.publicxarrepo;


import com.evolvedbinary.j8fu.function.RunnableE;
import org.junit.Test;

import static org.junit.Assert.*;

public class SemanticVersionTest {

    @Test
    public void parseMajorVersion() {
        SemanticVersion semanticVersion;

        semanticVersion = SemanticVersion.parse("123");
        assertEquals(123, semanticVersion.getMajor());
        assertEquals("123.0.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("456.321");
        assertEquals(456, semanticVersion.getMajor());
        assertEquals("456.321.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("27.0.0");
        assertEquals(27, semanticVersion.getMajor());
        assertEquals("27.0.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("934.888.999");
        assertEquals(934, semanticVersion.getMajor());
        assertEquals("934.888.999", semanticVersion.toString());

        assertThrows(IllegalArgumentException.class, () ->
                SemanticVersion.parse("abc")
        );

        assertThrows(IllegalArgumentException.class, () ->
                SemanticVersion.parse("abc.1")
        );

        assertThrows(IllegalArgumentException.class, () ->
                SemanticVersion.parse("abc.1.2")
        );
    }

    @Test
    public void parseMinorVersion() {
        SemanticVersion semanticVersion;

        semanticVersion = SemanticVersion.parse("331");
        assertEquals(0, semanticVersion.getMinor());
        assertEquals("331.0.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("123.567");
        assertEquals(567, semanticVersion.getMinor());
        assertEquals("123.567.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("456.321");
        assertEquals(321, semanticVersion.getMinor());
        assertEquals("456.321.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("27.0.0");
        assertEquals(0, semanticVersion.getMinor());
        assertEquals("27.0.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("0.15");
        assertEquals(15, semanticVersion.getMinor());
        assertEquals("0.15.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("0.15.0");
        assertEquals(15, semanticVersion.getMinor());
        assertEquals("0.15.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("934.888.999");
        assertEquals(888, semanticVersion.getMinor());
        assertEquals("934.888.999", semanticVersion.toString());

        assertThrows(IllegalArgumentException.class, () ->
                SemanticVersion.parse("27.abc")
        );

        assertThrows(IllegalArgumentException.class, () ->
                SemanticVersion.parse("27.abc.0")
        );
    }

    @Test
    public void parsePatchVersion() {
        SemanticVersion semanticVersion;

        semanticVersion = SemanticVersion.parse("15");
        assertEquals(0, semanticVersion.getPatch());
        assertEquals("15.0.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("27.0.0");
        assertEquals(0, semanticVersion.getPatch());
        assertEquals("27.0.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("0.0.12");
        assertEquals(12, semanticVersion.getPatch());
        assertEquals("0.0.12", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("934.888.999");
        assertEquals(999, semanticVersion.getPatch());
        assertEquals("934.888.999", semanticVersion.toString());

        assertThrows(IllegalArgumentException.class, () ->
                SemanticVersion.parse("27.0.abc")
        );
    }

    @Test
    public void parsePreReleaseVersion() {
        SemanticVersion semanticVersion;

        semanticVersion = SemanticVersion.parse("15");
        assertNull(semanticVersion.getPreReleaseLabel());
        assertEquals("15.0.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("15.4");
        assertNull(semanticVersion.getPreReleaseLabel());
        assertEquals("15.4.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("27.0.0");
        assertNull(semanticVersion.getPreReleaseLabel());
        assertEquals("27.0.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("27.0.0-RC6");
        assertEquals("RC6", semanticVersion.getPreReleaseLabel());
        assertEquals("27.0.0-RC6", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("27.0.0-RC6.1.3");
        assertEquals("RC6.1.3", semanticVersion.getPreReleaseLabel());
        assertEquals("27.0.0-RC6.1.3", semanticVersion.toString());

        assertThrows(IllegalArgumentException.class, () ->
            SemanticVersion.parse("27.0.0-RC6.1.3-xx")
        );
    }

    @Test
    public void parseBuildVersion() {
        SemanticVersion semanticVersion;

        semanticVersion = SemanticVersion.parse("15");
        assertNull(semanticVersion.getBuildLabel());
        assertEquals("15.0.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("15.4");
        assertNull(semanticVersion.getBuildLabel());
        assertEquals("15.4.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("27.0.0");
        assertNull(semanticVersion.getBuildLabel());
        assertEquals("27.0.0", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("27.0.0-RC6");
        assertNull(semanticVersion.getBuildLabel());
        assertEquals("27.0.0-RC6", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("27.0.0-RC6.1.3");
        assertNull(semanticVersion.getBuildLabel());
        assertEquals("27.0.0-RC6.1.3", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("27.0.0+hello");
        assertEquals("hello", semanticVersion.getBuildLabel());
        assertEquals("27.0.0+hello", semanticVersion.toString());

        semanticVersion = SemanticVersion.parse("27.0.0-RC6.1.3+goodbye");
        assertEquals("goodbye", semanticVersion.getBuildLabel());
        assertEquals("27.0.0-RC6.1.3+goodbye", semanticVersion.toString());
    }

    private static <T extends Exception> void assertThrows(final Class<T> clazz, final RunnableE<T> lambda) {
        try {
            lambda.run();
            fail("Excepted " + clazz.getName() + ", but no exception was thrown");
        } catch (final Exception e) {
            assertEquals(e.getMessage(), clazz, e.getClass());
        }
    }
}
