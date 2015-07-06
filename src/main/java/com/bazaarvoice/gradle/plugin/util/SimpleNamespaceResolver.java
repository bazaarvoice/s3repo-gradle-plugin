package com.bazaarvoice.gradle.plugin.util;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;


public final class SimpleNamespaceResolver implements NamespaceContext {

    public static SimpleNamespaceResolver forPrefixAndNamespace(String prefix, String namespaceUri) {
        return new SimpleNamespaceResolver(prefix, namespaceUri);
    }

    private final String prefix;
    private final String namespaceUri;

    private SimpleNamespaceResolver(String prefix, String namespaceUri) {
        this.prefix = prefix;
        this.namespaceUri = namespaceUri;
    }

    public String getNamespaceURI(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException();
        } else if (prefix.equals(this.prefix)) {
            return namespaceUri;
        } else if (XMLConstants.XML_NS_PREFIX.equals(prefix)) {
            return XMLConstants.XML_NS_URI;
        } else if (XMLConstants.XMLNS_ATTRIBUTE.equals(prefix)) {
            return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
        } else {
            return null;
        }
    }

    public String getPrefix(String namespaceUri) {
        if (namespaceUri == null) {
            throw new IllegalArgumentException();
        } else if (namespaceUri.equals(this.namespaceUri)) {
            return prefix;
        } else if (namespaceUri.equals(XMLConstants.XML_NS_URI)) {
            return XMLConstants.XML_NS_PREFIX;
        } else if (namespaceUri.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
            return XMLConstants.XMLNS_ATTRIBUTE;
        } else {
            return null;
        }
    }

    public Iterator<String> getPrefixes(String namespaceUri) {
        final String prefix = getPrefix(namespaceUri);
        if (this.prefix.equals(prefix)) {
            return Arrays.asList(prefix).iterator();
        } else if (prefix == null) {
            return Collections.<String>emptyList().iterator();
        } else {
            return Arrays.asList(prefix).iterator();
        }
    }

}