package org.exist.maven.plugins.publicxarrepo;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;

import java.io.File;

public class ResolveMojoTest extends AbstractMojoTestCase {

    @Override
    protected void setUp() throws Exception {
        // required for mojo lookups to work
        getContainer().getContext().put("project.groupId", "blah");
        super.setUp();

    }

    public void testMojoGoal() throws Exception {
        final File testPom = new File(getBasedir(), "src/test/resources/unit/resolve-basic-test/pom.xml");
        final ResolveMojo mojo = (ResolveMojo) lookupMojo("resolve", testPom);
        assertNotNull(mojo);

        assertEquals("http://some-other-repo.com", mojo.getRepoUri());
        final PackageInfo packageInfo = new PackageInfo("no-sha256", "1.0.0", "some-package.xar");
        assertEquals("http://some-other-repo.com/public/some-package.xar", mojo.getPackageUri(packageInfo));
    }
}
