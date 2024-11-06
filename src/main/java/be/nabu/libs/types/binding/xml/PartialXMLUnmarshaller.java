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
	
	private boolean camelCaseDashes, camelCaseUnderscores, trimContent = true, ignoreUndefined;
	
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
			XMLParserSAX saxParser = new XMLParserSAX(type, windows, values);
			saxParser.setCamelCaseDashes(camelCaseDashes);
			saxParser.setCamelCaseUnderscores(camelCaseUnderscores);
			saxParser.setTrimContent(trimContent);
			saxParser.setIgnoreUndefined(ignoreUndefined);
			saxParser.setCharset(charset);
			XMLParserStAX staxParser = new XMLParserStAX(saxParser);
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

	public boolean isTrimContent() {
		return trimContent;
	}

	public void setTrimContent(boolean trimContent) {
		this.trimContent = trimContent;
	}

	public boolean isIgnoreUndefined() {
		return ignoreUndefined;
	}

	public void setIgnoreUndefined(boolean ignoreUndefined) {
		this.ignoreUndefined = ignoreUndefined;
	}
	
}
