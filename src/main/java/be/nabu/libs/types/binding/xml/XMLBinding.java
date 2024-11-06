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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.types.BaseTypeInstance;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.MarshalException;
import be.nabu.libs.types.binding.BaseTypeBinding;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.CharBuffer;
import be.nabu.utils.io.api.ReadableContainer;

/**
 * There is no configuration for the XML binding
 * It is possible that the xml is not in the exact format you want and you want to perform things like date transformation
 * I purposely did not add this functionality to the xmlbinding (and similar bindings like json) because there is already a mapping engine
 * The idea is you make a type that matches the xml to parse and a target type that you want and perform mapping between them
 * Adding what amounts to tiny mapping engines to the different bindings makes things more complex (different ways of transforming things) and less manageable due to code bloat
 * 
 * Note: there is the annoying problem if say dates, the string format of the date is not (arguably) inherent to the data structure
 * However the data structure can only have one format so it is either the on you want to parse from or it is not
 * If not, you need to make an identical data structure but with a different format...
 * These "meta" descriptive elements are all contained in the "properties" of the elements
 * Perhaps provide a way to "overlay" different properties on a data type? at runtime this can be handled by the parent complex type: 
 * 		just wrap the actual element in a class that returns different attributes and wraps its children
 * 
 * Can actually make this a new complex type? It can wrap around _any_ other complex type and modify descriptive properties?
 * It never extends and fully reflects the original type?
 * 		> not sure what the effect will be on typecasting?
 * 		> uses typeutils to check if they are compatible, should be ok
 */
public class XMLBinding extends BaseTypeBinding {
	
	private Charset charset;
	private ComplexType type;
	private boolean trimContent = false, camelCaseDashes, camelCaseUnderscores, ignoreUndefined, allowSuperTypes, forceRootTypeMatch, prettyPrint = true, unwrapBeans, multilineAttributes, multilineInAttributes, allowXSI = true, allowRootNull = true;
	
	public XMLBinding(ComplexType type, Charset charset) {
		this.charset = charset;
		this.type = type;
	}

	@Override
	protected ComplexContent unmarshal(ReadableResource resource, Window [] windows, Value<?>...values) throws IOException {
		// create the sax handler
		XMLParserSAX saxHandler = new XMLParserSAX(type, windows, values);
		saxHandler.setCamelCaseDashes(camelCaseDashes);
		saxHandler.setCamelCaseUnderscores(camelCaseUnderscores);
		saxHandler.setTrimContent(isTrimContent());
		saxHandler.setIgnoreUndefined(ignoreUndefined);
		saxHandler.setResource(resource);
		saxHandler.setCharset(charset);
		saxHandler.setAllowSuperTypes(allowSuperTypes);
		saxHandler.setForceRootTypeMatch(forceRootTypeMatch);
		saxHandler.setUnwrapBeans(unwrapBeans);
		saxHandler.setAllowRootNull(allowRootNull);
		return unmarshal(saxHandler, resource, windows, values);
	}

	public ComplexContent unmarshal(XMLParserSAX saxHandler, ReadableResource resource, Window[] windows, Value<?>...values) throws IOException {
		// create the reader
		ReadableContainer<CharBuffer> readable = IOUtils.wrapReadable(resource.getReadable(), charset);
		Reader reader = IOUtils.toReader(readable);

		// without windows, use the sax parser, it is up to 10x faster than the stax parser
		if (windows.length == 0) {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setValidating(false);
			try {
				SAXParser parser = factory.newSAXParser();
				
				// if you disable it like this and there is actually a dtd with a remote reference
				// it will _not_ ignore the dtd but instead fail because it is not allowed to access the necessary files...
//				parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "false");
//				parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "false");
//				parser.setProperty(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "false");
				
				XMLReader xmlReader = parser.getXMLReader();
				
				// disable everything with dtd and loading external files for it...
				xmlReader.setFeature("http://xml.org/sax/features/validation", false);
				xmlReader.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
				xmlReader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
				xmlReader.setFeature("http://xml.org/sax/features/external-general-entities", false);
				xmlReader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				xmlReader.setFeature("http://xml.org/sax/features/use-entity-resolver2", false);   
				xmlReader.setFeature("http://xml.org/sax/features/resolve-dtd-uris", false);
				xmlReader.setFeature("http://apache.org/xml/features/validation/dynamic", false);
				xmlReader.setFeature("http://apache.org/xml/features/validation/schema/augment-psvi", false);
				// this one fails, so leaving it out
//				reader2.setFeature("http://apache.org/xml/features/validation/unparsed-entity-checking", false);
				
				parser.parse(new InputSource(reader), saxHandler);
				return saxHandler.getInstance();
			}
			catch (ParserConfigurationException e) {
				throw new MarshalException("Could not parse: " + resource, e);
			}
			catch (SAXException e) {
				throw new MarshalException("Could not parse: " + resource, e);
			}			
		}
		// when using windows, we need the stax parser for the character offsets
		else {
			XMLInputFactory factory = XMLInputFactory.newFactory();
			// simply don't return anything seems to work for stax, so let's leave it at that for now
			factory.setXMLResolver(new XMLResolver() {
				@Override
				public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace) throws XMLStreamException {
					return new ByteArrayInputStream(new byte[0]);
				}
			});
//			factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
//			factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "false");
//			factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "false");
//			factory.setProperty(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "false");
			try {
				// set up the stax parser
				XMLParserStAX xmlParser = new XMLParserStAX(saxHandler);
				// do the actual parsing
				XMLStreamReader parser = factory.createXMLStreamReader(reader);
				xmlParser.parse(parser);
				return xmlParser.getInstance();
			}
			catch (XMLStreamException e) {
				throw new IOException(e);
			}
			catch (SAXException e) {
				throw new MarshalException(e);
			}
		}
	}

	@Override
	public void marshal(OutputStream output, ComplexContent content, Value<?>... values) throws IOException {
		XMLMarshaller xmlMarshaller = new XMLMarshaller(new BaseTypeInstance(type, values));
		xmlMarshaller.setPrettyPrint(prettyPrint);
		xmlMarshaller.setMultilineAttributes(multilineAttributes);
		xmlMarshaller.setMultilineInAttributes(multilineInAttributes);
		xmlMarshaller.setAllowXSI(allowXSI);
		xmlMarshaller.marshal(output, charset, content);
	}

	public boolean isTrimContent() {
		return trimContent;
	}

	public void setTrimContent(boolean trimContent) {
		this.trimContent = trimContent;
	}

	public boolean isCamelCaseDashes() {
		return camelCaseDashes;
	}

	public void setCamelCaseDashes(boolean camelCaseDashes) {
		this.camelCaseDashes = camelCaseDashes;
	}

	public boolean isCamelCaseUnderscores() {
		return camelCaseUnderscores;
	}

	public void setCamelCaseUnderscores(boolean camelCaseUnderscores) {
		this.camelCaseUnderscores = camelCaseUnderscores;
	}

	public boolean isIgnoreUndefined() {
		return ignoreUndefined;
	}

	public void setIgnoreUndefined(boolean ignoreUndefined) {
		this.ignoreUndefined = ignoreUndefined;
	}

	public boolean isAllowSuperTypes() {
		return allowSuperTypes;
	}

	public void setAllowSuperTypes(boolean allowSuperTypes) {
		this.allowSuperTypes = allowSuperTypes;
	}

	public boolean isForceRootTypeMatch() {
		return forceRootTypeMatch;
	}

	public void setForceRootTypeMatch(boolean forceRootTypeMatch) {
		this.forceRootTypeMatch = forceRootTypeMatch;
	}

	public boolean isPrettyPrint() {
		return prettyPrint;
	}

	public void setPrettyPrint(boolean prettyPrint) {
		this.prettyPrint = prettyPrint;
	}

	public boolean isUnwrapBeans() {
		return unwrapBeans;
	}

	public void setUnwrapBeans(boolean unwrapBeans) {
		this.unwrapBeans = unwrapBeans;
	}

	public boolean isMultilineAttributes() {
		return multilineAttributes;
	}

	public void setMultilineAttributes(boolean multilineAttributes) {
		this.multilineAttributes = multilineAttributes;
	}

	public boolean isMultilineInAttributes() {
		return multilineInAttributes;
	}

	public void setMultilineInAttributes(boolean multilineInAttributes) {
		this.multilineInAttributes = multilineInAttributes;
	}

	public boolean isAllowXSI() {
		return allowXSI;
	}

	public void setAllowXSI(boolean allowXSI) {
		this.allowXSI = allowXSI;
	}

	public boolean isAllowRootNull() {
		return allowRootNull;
	}

	public void setAllowRootNull(boolean allowRootNull) {
		this.allowRootNull = allowRootNull;
	}

	public Charset getCharset() {
		return charset;
	}

	public ComplexType getType() {
		return type;
	}

}
