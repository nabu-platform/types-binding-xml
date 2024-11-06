/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.types.binding.xml;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.binding.api.Window;

/**
 * IMPORTANT: there is a difference in character offsets amongst stax parsers:
 * 
 * xerces will return the END of an event + 4
 * Why the additional offset of 4, no idea, this appears to be a bug
 * 
 * woodstox (4.1.1) returns the BEGINNING of an event which is exactly what we need
 * 
 * For this reason, large documents require woodstox for parsing
 * You require following libraries for woodstox: woodstox-core-asl-4.1.1.jar && stax2-api-3.1.1.jar
 * These are provided out of the box by jboss 7.1.1 as an osgi module
 * 
 * @author alex
 *
 */
public class XMLParserStAX {
	
//	public static ComplexContent unmarshal(InputStream input, ComplexType type) throws UnsupportedEncodingException, XMLStreamException, SAXException {
//		XMLInputFactory factory = XMLInputFactory.newFactory();
//		Reader reader = new InputStreamReader(input, "UTF-8");
//		XMLStreamReader parser = factory.createXMLStreamReader(reader);
//		XMLParserStAX xmlParser = new XMLParserStAX(type);
//		xmlParser.parse(parser);
//		return xmlParser.getInstance();
//	}
//	
	private XMLParserSAX saxParser;
	
	public XMLParserStAX(ComplexType type, Window [] windows, Value<?>...values) {
		saxParser = new XMLParserSAX(type, windows, values);
	}
	
	XMLParserStAX(XMLParserSAX saxParser) {
		this.saxParser = saxParser;
	}

	public ComplexContent getInstance() {
		return saxParser.getInstance();
	}

	public void parse(XMLStreamReader reader) throws XMLStreamException, SAXException {
		saxParser.clear();
		while (reader.hasNext()) {
			int eventType = reader.next();
			switch (eventType) {
				case XMLEvent.START_ELEMENT:
					if (reader.getPrefix() != null && !reader.getPrefix().isEmpty()) {
						saxParser.startPrefixMapping(reader.getPrefix(), reader.getNamespaceURI());
					}
					saxParser.setOffset(reader.getLocation().getCharacterOffset());
					AttributesImpl attributes = new AttributesImpl();
					for (int i = 0; i < reader.getAttributeCount(); i++) {
						if (reader.getAttributePrefix(i) != null && !reader.getAttributePrefix(i).isEmpty()) {
							saxParser.startPrefixMapping(reader.getAttributePrefix(i), reader.getAttributeNamespace(i));
						}
						attributes.addAttribute(
							reader.getAttributeNamespace(i),
							reader.getAttributeLocalName(i),
							reader.getAttributeName(i).toString(),
							reader.getAttributeType(i), 
							reader.getAttributeValue(i));
					}
					saxParser.startElement(reader.getNamespaceURI(), reader.getLocalName(), reader.getName().toString(), attributes);
				break;
				case XMLEvent.END_ELEMENT:
					saxParser.endElement(reader.getNamespaceURI(), reader.getLocalName(), reader.getName().toString());
				break;
				case XMLEvent.CHARACTERS:
					saxParser.characters(reader.getText().toCharArray(), 0, reader.getText().length());
				break;
				case XMLEvent.NAMESPACE:
					saxParser.startPrefixMapping(reader.getPrefix(), reader.getNamespaceURI());
				break;
			}
			if (saxParser.isDone()) {
				break;
			}
		}
	}
}
