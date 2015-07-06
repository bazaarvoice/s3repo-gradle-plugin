package com.bazaarvoice.gradle.plugin.util;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public final class XmlUtils {

    private XmlUtils() {}

    /** Parse the provided {@link File} as XML, decompressing it first if it has a .gz file extension. */
    public static Document parseXmlFile(File file) throws IOException {
        InputStream in = toInputStream(file);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(in);
        } catch (Exception e) {
            throw new RuntimeException("failed to parse", e);
        } finally {
            in.close();
        }
    }

    private static InputStream toInputStream(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        if (file.getName().endsWith(".gz")) {
            in = new GZIPInputStream(in);
        }
        return in;
    }


}
