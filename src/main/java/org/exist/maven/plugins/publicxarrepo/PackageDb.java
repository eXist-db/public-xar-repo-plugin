package org.exist.maven.plugins.publicxarrepo;

import com.evolvedbinary.j8fu.Either;
import com.evolvedbinary.j8fu.OptionalUtil;
import com.evolvedbinary.j8fu.function.ConsumerE;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.io.output.CountingOutputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.evolvedbinary.j8fu.Either.Left;
import static com.evolvedbinary.j8fu.Either.Right;
import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.maven.plugins.publicxarrepo.PackageInfo.METADATA_FILE_EXTENSION;
import static org.exist.maven.plugins.publicxarrepo.XmlUtils.DOCUMENT_BUILDER_FACTORY;

/**
 * Just a mapping from either {@link Package#abbrev} or {@link Package#name} to {@link PackageInfo#path}.
 *
 * Used by the {@link CacheManager}.
 */
public class PackageDb {

    private static final String PACKAGE_DB_FILENAME = "packages.xml";

    public static List<PackageInfo> findPackageInfos(final Path dir, final AbbrevAndOrName abbrevName)
            throws IOException {
        final Path packageDb = dir.resolve(PACKAGE_DB_FILENAME);
        if (!Files.exists(packageDb)) {
            return Collections.emptyList();
        }

        try (final FileInputStream is = new FileInputStream(packageDb.toFile())) {
            final List<PackageInfo> packageInfos = new ArrayList<>();

            final FileLock lock = is.getChannel().lock(0L, Long.MAX_VALUE, true);
            try {
                withPackages(is, packageElem -> {

                    // does this match the name or abbrev
                    boolean matches = false;
                    if (abbrevName.getName().isPresent()) {
                        matches = abbrevName.getName().get().equals(packageElem.getAttribute("name"));
                    }
                    if (matches == false && abbrevName.getAbbrev().isPresent()) {
                        matches = abbrevName.getAbbrev().get().equals(packageElem.getAttribute("abbrev"));
                    }

                    if (matches) {
                        withPackageInfos(packageElem, packageInfoElem -> {
                            final String packageInfoElemPath = packageInfoElem.getAttribute("path");
                            final Path packageInfoFile = dir.resolve(packageInfoElemPath);
                            try (final InputStream pkgInfoIs = Files.newInputStream(packageInfoFile)) {
                                packageInfos.add(PackageInfo.deserialize(new InputSource(pkgInfoIs)));
                            } catch (final ParserConfigurationException | SAXException e) {
                                throw new IOException(e);
                            }
                        });
                    }
                });
            } catch (final ParserConfigurationException | SAXException e) {
                throw new IOException(e);
            } finally {
                // check an exception has not closed the channel
                if (lock.channel() != null && lock.channel().isOpen()) {
                    lock.close();
                }
            }

            return packageInfos;
        }
    }

    public static void addPackageInfo(final Path dir, final AbbrevAndOrName abbrevName,
            final PackageInfo pkgInfo) throws IOException {
        final Path packageDb = dir.resolve(PACKAGE_DB_FILENAME);

        try (final RandomAccessFile randomAccessFile = new RandomAccessFile(packageDb.toFile(), "rw")) {
            final FileLock lock = randomAccessFile.getChannel().lock();
            try {

                // read into memory the current state of the database from disk
                final Map<Either<String, String>, Set<String>> db;
                if (randomAccessFile.length() > 0) {
                    // NOTE: no need to close as opened on file descriptor, we also shield against loadDb closing the stream
                    final InputStream is = new CloseShieldInputStream(new FileInputStream(randomAccessFile.getFD()));
                    db = loadDb(is);
                } else {
                    // new db
                    db = new LinkedHashMap<>();
                }

                /* we de-normalise the abbrev and name into two separate entries in the db */

                // add the new packageInfo to the database for the name
                if (abbrevName.getName().isPresent()) {
                    addToDb(db, Right(abbrevName.getName().get()), pkgInfo);
                }

                // add the new packageInfo to the database for the abbrev
                if (abbrevName.getAbbrev().isPresent()) {
                    addToDb(db, Left(abbrevName.getAbbrev().get()), pkgInfo);
                }

                // write out the new database to disk
                randomAccessFile.seek(0);

                // NOTE: no need to close as opened on file descriptor
                final CountingOutputStream os = new CountingOutputStream(new FileOutputStream(randomAccessFile.getFD()));
                saveDb(db, os);

                // truncate any remaining bytes
                randomAccessFile.setLength(os.getByteCount());

            } finally {
                // check an exception has not closed the channel
                if (lock.channel() != null && lock.channel().isOpen()) {
                    lock.close();
                }
            }
        } catch (final ParserConfigurationException | SAXException | TransformerException e) {
            throw new IOException(e);
        }
    }

    private static void addToDb(final Map<Either<String, String>, Set<String>> db,
            final Either<String, String> key, final PackageInfo pkgInfo) {
        Set<String> currentEntry = db.get(key);
        if (currentEntry == null) {
            currentEntry = new LinkedHashSet<>();
            db.put(key, currentEntry);
        }
        currentEntry.add(pkgInfo.getPath() + METADATA_FILE_EXTENSION);
    }

    private static void saveDb(final Map<Either<String, String>, Set<String>> db, final OutputStream os)
            throws ParserConfigurationException, TransformerException {
        final DocumentBuilder documentBuilder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        final Document document = documentBuilder.newDocument();
        final Element packagesElem = document.createElement("packages");

        for (final Map.Entry<Either<String, String>, Set<String>> entries : db.entrySet()) {
            final Element packageElem = document.createElement("package");
            final Tuple2<String, String> attr = entries.getKey().fold(
                    abbrev -> Tuple("abbrev", abbrev),
                    name -> Tuple("name", name)
            );
            packageElem.setAttribute(attr._1, attr._2);
            for (final String packageInfoPath : entries.getValue()) {
                final Element packageInfo = document.createElement("packageInfo");
                packageInfo.setAttribute("path", packageInfoPath);
                packageElem.appendChild(packageInfo);
            }
            packagesElem.appendChild(packageElem);
        }
        document.appendChild(packagesElem);

        XmlUtils.serialize(document, new StreamResult(os));
    }

    private static Map<Either<String, String>, Set<String>> loadDb(final InputStream is)
            throws IOException, SAXException, ParserConfigurationException {
        final Map<Either<String, String>, Set<String>> db = new LinkedHashMap<>();
        withPackages(is, packageElem -> {
            final Set<String> packageInfoPaths = new LinkedHashSet<>();
            withPackageInfos(packageElem, packageInfoElem -> {
                final String path = packageInfoElem.getAttribute("path");
                packageInfoPaths.add(path);
            });

            final Either<String, String> packageAbbrevOrName = OptionalUtil.toLeft(
                    Optional.ofNullable(packageElem.getAttribute("abbrev"))
                            .filter(s -> !s.isEmpty())
                    ,
                    () -> packageElem.getAttribute("name"));

            db.put(packageAbbrevOrName, packageInfoPaths);
        });
        return db;
    }

    private static void withPackages(final InputStream is, final ConsumerE<Element, IOException> packageConsumer)
            throws ParserConfigurationException, IOException, SAXException {
        final DocumentBuilder documentBuilder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        final Document document = documentBuilder.parse(is);
        final Element root = document.getDocumentElement();
        final NodeList packageElems = root.getElementsByTagName("package");
        for (int i = 0; i < packageElems.getLength(); i++) {
            final Element packageElem = (Element) packageElems.item(i);
            packageConsumer.accept(packageElem);
        }
    }

    private static void withPackageInfos(final Element packageElem,
            final ConsumerE<Element, IOException> packageInfoConsumer) throws IOException {
        final NodeList packageInfoElems = packageElem.getElementsByTagName("packageInfo");
        for (int i = 0; i < packageInfoElems.getLength(); i++) {
            final Element packageInfoElem = (Element) packageInfoElems.item(i);
            packageInfoConsumer.accept(packageInfoElem);
        }
    }

    public static class AbbrevAndOrName extends Tuple2<Optional<String>, Optional<String>> {
        public AbbrevAndOrName(final Optional<String> abbrev, final Optional<String> name) {
            super(abbrev, name);
        }

        public Optional<String> getAbbrev() {
            return _1;
        }

        public Optional<String> getName() {
            return _2;
        }
    }
}
