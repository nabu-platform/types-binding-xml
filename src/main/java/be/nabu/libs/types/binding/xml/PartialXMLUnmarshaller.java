package be.nabu.libs.types.binding.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.xml.sax.SAXException;

import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.MarshalException;
import be.nabu.libs.types.binding.api.PartialUnmarshaller;
import be.nabu.libs.types.binding.api.Window;

import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.sr.BasicStreamReader;

public class PartialXMLUnmarshaller implements PartialUnmarshaller {

	private ComplexType type;
	private Charset charset;
	private Window [] windows;
	private Value<?> [] values;
	private Map<String, String> namespaces;
	
	public PartialXMLUnmarshaller(Map<String, String> namespaces, ComplexType type, Charset charset, Window [] windows, Value<?>...values) {
		this.type = type;
		this.charset = charset;
		this.windows = windows;
		this.values = values;
		this.namespaces = namespaces;
	}
	
	@Override
	public List<ComplexContent> unmarshal(InputStream input, long offset, int batchSize) throws IOException {
		Reader reader = new InputStreamReader(input, charset);
		// go to the correct position
		reader.skip(offset);
		
		XMLInputFactory factory = XMLInputFactory.newFactory();
		if (factory instanceof com.ctc.wstx.stax.WstxInputFactory) {
//			factory.setProperty(WstxInputProperties.P_INPUT_PARSING_MODE, WstxInputProperties.PARSING_MODE_DOCUMENTS);
			factory.setProperty(WstxInputProperties.P_INPUT_PARSING_MODE, WstxInputProperties.PARSING_MODE_FRAGMENT);
		}
		else
			throw new IOException("You are requesting windowed mode, this is currently only supported with woodstox");
		try {
			XMLStreamReader streamReader = factory.createXMLStreamReader(reader);
			// we need to register the namespaces we encountered
			for (String prefix : namespaces.keySet()) {
				((BasicStreamReader) streamReader).getInputElementStack().addNsBinding(
					prefix, namespaces.get(prefix));
			}
			XMLParserStAX staxParser = new XMLParserStAX(type, windows, values);
			List<ComplexContent> results = new ArrayList<ComplexContent>();
			for (int i = 0; i < batchSize; i++) {
				staxParser.parse(streamReader);
				results.add(staxParser.getInstance());
			}
			return results;
		}
		catch (XMLStreamException e) {
			throw new IOException(e);
		}
		catch (SAXException e) {
			throw new MarshalException(e);
		}
	}
}
