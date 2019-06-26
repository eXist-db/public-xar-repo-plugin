package org.exist.maven.plugins.publicxarrepo;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;

import static javax.xml.transform.OutputKeys.*;

class XmlUtils {
    static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();
    static {
        DOCUMENT_BUILDER_FACTORY.setNamespaceAware(true);
    }
    static final TransformerFactory TRANSFORMER_FACTORY = TransformerFactory.newInstance();

    public static void serialize(final Document document, final Result result) throws TransformerException {
        final Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
        transformer.setOutputProperty(ENCODING, "UTF-8");
        transformer.setOutputProperty(METHOD, "xml");
        transformer.setOutputProperty(INDENT, "yes");
        transformer.setOutputProperty(STANDALONE, "yes");
        transformer.setOutputProperty(OMIT_XML_DECLARATION, "yes");
        final DOMSource source = new DOMSource(document);
        transformer.transform(source, result);
    }
}
