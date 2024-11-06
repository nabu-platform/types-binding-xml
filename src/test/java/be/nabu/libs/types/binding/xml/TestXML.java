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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.ParseException;

import javax.xml.parsers.ParserConfigurationException;

import junit.framework.TestCase;

import org.xml.sax.SAXException;

import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.binding.BindingConfig;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.java.BeanInstance;
import be.nabu.libs.types.java.BeanType;

public class TestXML extends TestCase {

	public void testSchemaImport() throws IOException, ParseException {
		InputStream input = TestXML.class.getClassLoader().getResourceAsStream("testSchemaImport.xml");
		XMLBinding binding = new XMLBinding(new BeanType<Note>(Note.class), Charset.forName("UTF-8"));
		Note note = TypeUtils.getAsBean(binding.unmarshal(input, new Window[0]), Note.class);
		assertEquals("you", note.getTo());
	}
	
	public void testSchemaImportWindow() throws IOException, ParseException {
		InputStream input = TestXML.class.getClassLoader().getResourceAsStream("testSchemaImport.xml");
		XMLBinding binding = new XMLBinding(new BeanType<Note>(Note.class), Charset.forName("UTF-8"));
		Note note = TypeUtils.getAsBean(binding.unmarshal(input, new Window[] {
			new Window("companies", 3, 3)
		}), Note.class);
		assertEquals("you", note.getTo());
	}
	
	public void testDTD() throws IOException, ParseException {
		InputStream input = TestXML.class.getClassLoader().getResourceAsStream("testDTD.xml");
		XMLBinding binding = new XMLBinding(new BeanType<Note>(Note.class), Charset.forName("UTF-8"));
		Note note = TypeUtils.getAsBean(binding.unmarshal(input, new Window[0]), Note.class);
		assertEquals("you", note.getTo());
	}
	
	public void testDTDWindow() throws IOException, ParseException {
		InputStream input = TestXML.class.getClassLoader().getResourceAsStream("testDTD.xml");
		XMLBinding binding = new XMLBinding(new BeanType<Note>(Note.class), Charset.forName("UTF-8"));
		Note note = TypeUtils.getAsBean(binding.unmarshal(input, new Window[] {
			new Window("companies", 3, 3)
		}), Note.class);
		assertEquals("you", note.getTo());
	}
	
	public void testXml() throws IOException, ParserConfigurationException, SAXException, ParseException {
		InputStream input = TestXML.class.getClassLoader().getResourceAsStream("test.xml");
		BindingConfig config = new BindingConfig();
		config.setComplexType("be.nabu.libs.types.binding.xml.Company");
		
		XMLBinding binding = new XMLBinding(new BeanType<Company>(Company.class), Charset.forName("UTF-8"));
		binding.setIgnoreUndefined(true);
		binding.setCamelCaseDashes(true);
		binding.setCamelCaseUnderscores(true);
		try {
			Window window = new Window("company/employees", 3, 3);

			Company result = TypeUtils.getAsBean(binding.unmarshal(input, new Window[] { window }), Company.class);
			assertEquals("Nabu", result.getName());
			assertEquals("Organizational", result.getUnit());
			assertEquals("Nabu HQ", result.getAddress());
			assertEquals("BE666-66-66", result.getBillingNumber());
			assertEquals(24, result.getEmployees().size());
			assertEquals("John1", result.getEmployees().get(1).getFirstName());
			// do multiple checks because of the window we set, these are in different batches
			assertEquals(new Integer(31), result.getEmployees().get(0).getAge());
			assertEquals(new Integer(57), result.getEmployees().get(1).getAge());
			assertEquals(new Integer(60), result.getEmployees().get(10).getAge());
			assertEquals(new Integer(44), result.getEmployees().get(14).getAge());
			assertEquals(new Integer(47), result.getEmployees().get(19).getAge());

			// there was a bug when reloading parts, this checks if it is ok
			assertEquals(new Integer(31), result.getEmployees().get(0).getAge());
			assertEquals(new Integer(57), result.getEmployees().get(1).getAge());
		}
		finally {
			input.close();
		}
	}
	
	public void testMap() throws IOException, ParseException {
		MapExample example = new MapExample("test1", "test2");
		XMLBinding binding = new XMLBinding(new BeanType<MapExample>(MapExample.class), Charset.forName("UTF-8"));
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		binding.marshal(output, new BeanInstance<MapExample>(example));
//		System.out.println(new String(output.toByteArray()));
		MapExample unmarshal = TypeUtils.getAsBean(binding.unmarshal(new ByteArrayInputStream(output.toByteArray()), new Window[0]), MapExample.class);
		assertEquals(unmarshal.getEntries(), example.getEntries());
		assertEquals(unmarshal.getSingle(), example.getSingle());
	}
}
