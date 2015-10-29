package be.nabu.libs.types.binding.xml;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.text.ParseException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
	
	public XMLBinding(ComplexType type, Charset charset) {
		this.charset = charset;
		this.type = type;
	}

	@Override
	protected ComplexContent unmarshal(ReadableResource resource, Window [] windows, Value<?>...values) throws IOException, ParseException {
		// create the sax handler
		XMLParserSAX saxHandler = new XMLParserSAX(type, windows, values);
		saxHandler.setResource(resource);
		saxHandler.setCharset(charset);
		
		// create the reader
		ReadableContainer<CharBuffer> readable = IOUtils.wrapReadable(resource.getReadable(), charset);
		Reader reader = IOUtils.toReader(readable);

		// without windows, use the sax parser, it is up to 10x faster than the stax parser
		if (windows.length == 0) {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);
			try {
				SAXParser parser = factory.newSAXParser();
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
			try {
				// set up the stax parser
				XMLParserStAX xmlParser = new XMLParserStAX(saxHandler);
				// do the actual parrsing
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
		new XMLMarshaller(new BaseTypeInstance(content.getType(), values))
			.marshal(output, charset, content);
	}
}
