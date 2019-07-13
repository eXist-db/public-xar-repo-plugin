# Public XAR Repo Maven Plugin

[![Build Status](https://travis-ci.com/eXist-db/public-xar-repo-plugin.svg?branch=master)](https://travis-ci.com/eXist-db/public-xar-repo-plugin)
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
