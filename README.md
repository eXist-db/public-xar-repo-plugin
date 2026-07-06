# Public XAR Repo Maven Plugin

[![CI](https://github.com/exist-db/public-xar-repo-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/exist-db/public-xar-repo-plugin/actions/workflows/ci.yml)
[![Java 8](https://img.shields.io/badge/java-8-blue.svg)](http://java.oracle.com)
[![License](https://img.shields.io/badge/license-LGPL%202.1-blue.svg)](https://www.gnu.org/licenses/lgpl-2.1.html)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.exist-db.maven.plugins/public-xar-repo-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.exist-db.maven.plugins/public-xar-repo-plugin)

A Maven plugin for resolving EXPath Packages from the Public Repo.


## Example Use

For example if you wanted to download the latest version of the `functx` and `markdown` packages from the eXist-db Public Repository for eXist-db version 4.7.0, you would place the following in your `pom.xml` file:

```xml
<plugin>
    <groupId>org.exist-db.maven.plugins</groupId>
    <artifactId>public-xar-repo-plugin</artifactId>
    <version>1.1.1</version>
    <executions>
        <execution>
            <id>fetch-xars</id>
            <phase>package</phase>
            <goals>
                <goal>resolve</goal>
            </goals>
            <configuration>
                <repoUri>http://exist-db.org/exist/apps/public-repo</repoUri>
                <existDbVersion>4.7.0</existDbVersion>
                <packages>
                    <package>
                        <abbrev>functx</abbrev>
                    </package>
                    <package>
                        <abbrev>markdown</abbrev>
                    </package>
                </packages>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Building

Requirements: Java 8+, Apache Maven 3.3+, Git.

```bash
mvn verify
```

To skip integration tests that download from the live public repository:

```bash
mvn verify -Dinvoker.skip=true
```

## Performing a Release

Releases use the [Maven Release Plugin](https://maven.apache.org/maven-release/maven-release-plugin/) together with the `public-xar-repo-release` profile, which attaches sources and Javadoc, signs artifacts with GPG, and uploads them to the [Sonatype Central Portal](https://central.sonatype.com/) via the [Central Publishing Maven Plugin](https://central.sonatype.org/publish/publish-portal-maven/).

Before releasing you need:

1. **GnuPG** with a key that is registered for `org.exist-db.maven.plugins` on Maven Central.
2. **A Central Portal user token** stored in your Maven `settings.xml` (see below).
3. A clean working tree with all changes committed.

### Maven `settings.xml`

The release profile expects a server entry with id `central` (matching `publishingServerId` in `pom.xml`). Add this to `~/.m2/settings.xml`, or to a project-local `settings.xml` passed with `mvn -s settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username><!-- Central Portal user token name --></username>
      <password><!-- Central Portal user token password --></password>
    </server>
  </servers>
</settings>
```

Generate the token in the Central Portal under **Account** → **Generate User Token**. Use the token **name** as `<username>` and the token **password** as `<password>`.

GPG signing is configured to use `gpg-agent` (`useAgent=true`). Make sure your signing key is loaded in the agent before you release, for example:

```bash
gpg --list-secret-keys
export GPG_TTY=$(tty)   # if gpg-agent cannot prompt for a passphrase
```

If you cannot use an agent, you may instead supply the passphrase via a Maven profile property in `settings.xml`:

```xml
<settings>
  <!-- ... servers ... -->
  <profiles>
    <profile>
      <id>gpg-signing</id>
      <properties>
        <gpg.passphrase><!-- your key passphrase --></gpg.passphrase>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>gpg-signing</activeProfile>
  </activeProfiles>
</settings>
```

Do not commit `settings.xml` with tokens or passphrases to version control.

### Release commands

From a clean clone with `settings.xml` configured:

```bash
mvn release:prepare
mvn release:perform
git push && git push --tags
```

The build uploads artifacts to the Central Portal and waits until they are **validated**. Log in at https://central.sonatype.com/ to review the deployment and publish it to Maven Central when ready.

See also the [official Maven publishing guide](https://central.sonatype.org/publish/publish-portal-maven/).
