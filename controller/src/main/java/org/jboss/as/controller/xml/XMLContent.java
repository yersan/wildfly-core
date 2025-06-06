/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.common.Assert;

/**
 * Encapsulates the content of an XML element.
 * @author Paul Ferraro
 * @param <RC> the reader context
 * @param <WC> the writer content
 */
public interface XMLContent<RC, WC> extends XMLContentWriter<WC> {

    /**
     * Reads element content into the specified reader context.
     * @param reader a StaX reader
     * @param context the reader context
     * @throws XMLStreamException if the input could not be read from the specified reader.
     */
    void readContent(XMLExtendedStreamReader reader, RC context) throws XMLStreamException;

    /**
     * Returns an empty content
     * @param <RC> the reader context
     * @param <WC> the writer context
     * @return an empty content
     */
    static <RC, WC> XMLContent<RC, WC> empty() {
        return new XMLContent<>() {
            @Override
            public void readContent(XMLExtendedStreamReader reader, RC value) throws XMLStreamException {
                Assert.assertTrue(reader.isStartElement());
                ParseUtils.requireNoContent(reader);
            }

            @Override
            public boolean isEmpty(WC content) {
                return true;
            }

            @Override
            public void writeContent(XMLExtendedStreamWriter streamWriter, WC value) throws XMLStreamException {
                // Do nothing
            }
        };
    }

    /**
     * Returns XML content whose text is consumed by the specified consumer during parsing.
     * @param <RC> the read context type
     * @param <RC> the write context type
     * @param consumer a consumer of text content
     * @return XML content whose text is consumed by the specified consumer.
     */
    static <RC, WC> XMLContent<RC, WC> of(BiConsumer<RC, String> consumer) {
        return of(consumer, Object::toString);
    }

    /**
     * Returns XML content whose text is consumed by the specified consumer during parsing
     * and formatted using the specified function during marshalling.
     * @param <RC> the read context type
     * @param <RC> the write context type
     * @param consumer a consumer of text content
     * @param formatter a formatter of text content
     * @return XML content whose text is consumed by the specified consumer.
     */
    static <RC, WC> XMLContent<RC, WC> of(BiConsumer<RC, String> consumer, Function<WC, String> formatter) {
        return new XMLContent<>() {
            @Override
            public void readContent(XMLExtendedStreamReader reader, RC context) throws XMLStreamException {
                consumer.accept(context, reader.getElementText());
            }

            @Override
            public void writeContent(XMLExtendedStreamWriter writer, WC content) throws XMLStreamException {
                if (!this.isEmpty(content)) {
                    AttributeMarshaller.marshallElementContent(formatter.apply(content), writer);
                }
            }

            @Override
            public boolean isEmpty(WC content) {
                return Optional.ofNullable(formatter.apply(content)).filter(Predicate.not(String::isEmpty)).isEmpty();
            }
        };
    }

    /**
     * Returns an empty content
     * @param <RC> the reader context
     * @param <WC> the writer context
     * @return an empty content
     */
    static <RC, WC> XMLContent<RC, WC> of(XMLElementGroup<RC, WC> group) {
        return !group.getReaderNames().isEmpty() ? new DefaultXMLContent<>(group) : empty();
    }

    class DefaultXMLContent<RC, WC> implements XMLContent<RC, WC> {
        private final XMLElementGroup<RC, WC> group;

        DefaultXMLContent(XMLElementGroup<RC, WC> group) {
            this.group = group;
        }

        @Override
        public void readContent(XMLExtendedStreamReader reader, RC context) throws XMLStreamException {
            // Validate entry criteria
            Assert.assertTrue(reader.isStartElement());
            QName parentElementName = reader.getName();
            int occurrences = 0;
            int maxOccurs = this.group.getCardinality().getMaxOccurs().orElse(Integer.MAX_VALUE);
            // Do nested elements exist?
            if (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
                // Read all nested elements
                do {
                    if (!this.group.getReaderNames().contains(reader.getName())) {
                        throw ParseUtils.unexpectedElement(reader, this.group.getReaderNames());
                    }
                    occurrences += 1;
                    // Validate maxOccurs
                    if (occurrences > maxOccurs) {
                        throw ParseUtils.maxOccursExceeded(reader, this.group.getNames(), this.group.getCardinality());
                    }
                    // Consumes 1 or more elements
                    this.group.getReader().readElement(reader, context);
                } while (reader.getEventType() != XMLStreamConstants.END_ELEMENT);
            } else {
                this.group.getReader().whenAbsent(context);
            }
            // Validate minOccurs
            if (occurrences < this.group.getCardinality().getMinOccurs()) {
                throw ParseUtils.minOccursNotReached(reader, this.group.getNames(), this.group.getCardinality());
            }
            // Validate exit criteria
            if (!reader.isEndElement()) {
                throw ParseUtils.unexpectedElement(reader);
            }
            if (!reader.getName().equals(parentElementName)) {
                throw ParseUtils.unexpectedEndElement(reader);
            }
        }

        @Override
        public boolean isEmpty(WC content) {
            return this.group.getWriter().isEmpty(content);
        }

        @Override
        public void writeContent(XMLExtendedStreamWriter writer, WC content) throws XMLStreamException {
            this.group.getWriter().writeContent(writer, content);
        }
    }
}
