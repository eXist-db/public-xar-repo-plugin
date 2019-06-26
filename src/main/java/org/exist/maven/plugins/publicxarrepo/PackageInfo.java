package org.exist.maven.plugins.publicxarrepo;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;

import java.io.IOException;

import static javax.xml.transform.OutputKeys.*;
import static org.exist.maven.plugins.publicxarrepo.XmlUtils.DOCUMENT_BUILDER_FACTORY;
import static org.exist.maven.plugins.publicxarrepo.XmlUtils.TRANSFORMER_FACTORY;

public class PackageInfo {

    public static final String METADATA_FILE_EXTENSION = ".xml";

    private final String sha256;
    private final String version;
    private final String path;

    public PackageInfo(final String sha256, final String version, final String path) {
        this.sha256 = sha256;
        this.version = version;
        this.path = path;
    }

    public String getSha256() {
        return sha256;
    }

    public String getVersion() {
        return version;
    }

    public String getPath() {
        return path;
    }

    public void serialize(final Result result) throws ParserConfigurationException, TransformerException {
        final DocumentBuilder documentBuilder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        final Document document = documentBuilder.newDocument();
        final Element element = document.createElement("packageInfo");
        element.setAttribute("sha256", getSha256());
        element.setAttribute("version", getVersion());
        element.setAttribute("path", getPath());
        document.appendChild(element);

        XmlUtils.serialize(document, result);
    }

    public static PackageInfo deserialize(final InputSource source) throws ParserConfigurationException, IOException, SAXException {
        final DocumentBuilder documentBuilder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        final Document document = documentBuilder.parse(source);
        final Element element = document.getDocumentElement();

        final String sha256 = element.getAttribute("sha256");
        final String version = element.getAttribute("version");
        final String path = element.getAttribute("path");

        return new PackageInfo(sha256, version, path);
    }
}
