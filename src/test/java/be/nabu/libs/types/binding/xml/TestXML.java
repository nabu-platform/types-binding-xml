package be.nabu.libs.types.binding.xml;

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
import be.nabu.libs.types.java.BeanType;

public class TestXML extends TestCase {
	
	public void testXml() throws IOException, ParserConfigurationException, SAXException, ParseException {
		InputStream input = TestXML.class.getClassLoader().getResourceAsStream("test.xml");
		BindingConfig config = new BindingConfig();
		config.setComplexType("be.nabu.libs.types.binding.xml.Company");
		
		XMLBinding binding = new XMLBinding(new BeanType<Company>(Company.class), Charset.forName("UTF-8"));
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
}
