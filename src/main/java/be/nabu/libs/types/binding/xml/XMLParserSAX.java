package be.nabu.libs.types.binding.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.types.BaseTypeInstance;
import be.nabu.libs.types.CollectionHandlerFactory;
import be.nabu.libs.types.DefinedTypeResolverFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.CollectionHandler;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.api.TypeRegistry;
import be.nabu.libs.types.api.Unmarshallable;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.DynamicElement;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.api.WindowedList;
import be.nabu.libs.types.java.BeanType;
import be.nabu.libs.types.properties.NameProperty;
import be.nabu.utils.codec.TranscoderUtils;
import be.nabu.utils.codec.impl.Base64Decoder;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;

/**
 * Does not seem to support any exact way to determine the location
 * You can use setDocumentLocator() to get a locator of the position after the event
 * but it only returns line numbers and column numbers, NOT actual position in the stream
 * @author alex
 *
 */
public class XMLParserSAX extends DefaultHandler {
	
	private CollectionHandler collectionHandler = CollectionHandlerFactory.getInstance().getHandler();
	
	private XMLBinding binding;
	
	private TypeRegistry registry;
	
	/**
	 * Whether xsi:type extensions are allowed
	 * All type implementations should allow this so it is enabled by default
	 */
	private boolean allowExtensions = true;
	
	private boolean trimContent = true;
	
	private boolean camelCaseDashes = false;
	private boolean camelCaseUnderscores = false;
	
	private boolean allowSuperTypes = false;
	
	private Charset charset = Charset.forName("UTF-8");
	
	private ReadableResource resource;
	
	private Map<String, String> namespaces = new HashMap<String, String>();
	
	/**
	 * If a field is encountered that does not exist in the definition, should it simply be ignored or should an exception be thrown?
	 */
	private boolean ignoreUndefined;
	
	/**
	 * When a field is ignored, we need to ignore all child fields as well
	 * The counter is increased for each start element when inside an ignored element and decreased on end element
	 * When the ignorecounter is 0 again we have exited the ignored element
	 */
	private int ignoreCounter = 0;
	
//	public static ComplexContent unmarshal(InputStream input, ComplexType type) throws ParserConfigurationException, SAXException, IOException {
//		SAXParserFactory factory = SAXParserFactory.newInstance();
//		factory.setNamespaceAware(true);
//		SAXParser parser = factory.newSAXParser();
//		XMLParserSAX xmlParser = new XMLParserSAX(type);
//		parser.parse(input, xmlParser);
//		return xmlParser.getInstance();
//	}
	
	/**
	 * The reader offset
	 */
	private int offset = -1;

	private ComplexType type;
	
	private Window [] windows;
	
	private ComplexContent instance;
	
	/**
	 * Keeps track (path-wise) where we are
	 */
	private Stack<String> pathStack = new Stack<String>();
	
	/**
	 * Keeps track of the collection indexes (for maps)
	 */
	private Stack<String> collectionIndexes = new Stack<String>();
	
	/**
	 * Keeps track of the instances of the complex elements that contain the other elements
	 */
	private Stack<ComplexContent> contentStack = new Stack<ComplexContent>();
	
	/**
	 * Keeps track of the element definitions that we use
	 */
	private Stack<Element<?>> elementStack = new Stack<Element<?>>();
	
	/**
	 * Keeps track of the any elements we are working with, they need to be set differently
	 */
	private Stack<String> anyStack = new Stack<String>();
	
	/**
	 * Per window it keeps track of the start of the current element for this window
	 */
	private Map<Window, Integer> windowOffsets = new HashMap<Window, Integer>();
	
	private boolean isSimpleType = false;
	private boolean isComplexType = false;
	/**
	 * the starting tag might explicitly set the value to nil
	 */
	private boolean isNil = false;
	
	/**
	 * When parsing a sub-path, you need to prepend the path to get correct hits in the stack
	 */
	private String pathPrefix = "";
	
	/**
	 * Keeps track of textual content
	 */
	private StringWriter content;
	
	private boolean firstElement = true;
	
	private Value<?> [] values;
	
	protected Window getWindow() {
		String currentPath = getCurrentPath();
		for (Window window : windows) {
			String windowPath = window.getPath();
			if (!windowPath.startsWith("/")) {
				windowPath = "/" + windowPath;
			}
			if (windowPath.equals(currentPath)) {
				return window;
			}
		}
		return null;
	}

	public XMLParserSAX(ComplexType type, Window [] windows, Value<?>...values) {
		if (type instanceof SimpleType) {
			throw new RuntimeException("The parser does not support simple types as root elements");
		}
		this.type = type;
		this.windows = windows;
		this.values = values;
	}
	
	public Charset getCharset() {
		return charset;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	public ReadableResource getResource() {
		return resource;
	}

	public void setResource(ReadableResource resource) {
		this.resource = resource;
	}

	@Override
	public void endPrefixMapping(String prefix) throws SAXException {
		namespaces.remove(prefix);
	}

	@Override
	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		namespaces.put(prefix, uri);
	}
	
	public void clear() {
		firstElement = true;
		contentStack.clear();
		instance = null;
	}

	private static String camelCaseCharacter(String name, char character) {
		StringBuilder builder = new StringBuilder();
		int index = -1;
		int lastIndex = 0;
		boolean first = true;
		while ((index = name.indexOf(character, index)) >= lastIndex) {
			String substring = name.substring(lastIndex, index);
			if (substring.isEmpty()) {
				continue;
			}
			else if (first) {
				builder.append(substring);
				first = false;
			}
			else {
				builder.append(substring.substring(0, 1).toUpperCase() + substring.substring(1));
			}
			lastIndex = index + 1;
		}
		if (lastIndex < name.length() - 1) {
			if (first) {
				builder.append(name.substring(lastIndex));
			}
			else {
				builder.append(name.substring(lastIndex, lastIndex + 1).toUpperCase()).append(name.substring(lastIndex + 1));
			}
		}
		return builder.toString();
	}
	
	private String preprocess(String name) {
		if (camelCaseDashes) {
			name = camelCaseCharacter(name, '-');
		}
		if (camelCaseUnderscores) {
			name = camelCaseCharacter(name, '_');			
		}
		return name;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		if (isNil) {
			throw new SAXException("Can't start an element " + localName + " inside an element that is explicitly set to nil");
		}
		
		if (ignoreCounter > 0) {
			ignoreCounter++;
			return;
		}
		
		if (localName.isEmpty()) {
			localName = qName;
		}
		
		localName = preprocess(localName);
		
		Map<String, String> elementAttributes = new HashMap<String, String>();

		Type actualType = null;
		
		// we need to go through the attributes first because it might contain things like xsi:type to indicate another type
		for (int i = 0; i < attributes.getLength(); i++) {
			String namespace = attributes.getURI(i);
			String name = attributes.getLocalName(i);
			// dedicated namespace for magic stuff
			if (namespace != null && namespace.equals(XMLMarshaller.XSI) && name != null) {
				if (name.equals("nil"))
					isNil = "true".equals(attributes.getValue(i));
				else if (name.equals("type")) {
					int index = attributes.getValue(i).indexOf(':');
					String prefix = index < 0 ? null : attributes.getValue(i).substring(0, index);
					String typeNamespace = namespaces.get(prefix);
					String typeId = index < 0 ? attributes.getValue(i) : attributes.getValue(i).substring(index + 1);
					// try to resolve it against the registry
					if (registry != null) {
						actualType = registry.getComplexType(typeNamespace, typeId);
						if (actualType == null) {
							actualType = registry.getSimpleType(typeNamespace, typeId);
						}
					}
					// if all else fails: resolve it against the the defined type factory
					if (actualType == null) {
						actualType = DefinedTypeResolverFactory.getInstance().getResolver().resolve(typeId);
					}
					// if we can not resolve the type, we probably have a problem
					// we might want to add a boolean to influence this behavior
					if (actualType == null) {
						throw new SAXException("Could not resolve actual xsi type: " + typeId);
					}
				}
			}
			else
				elementAttributes.put(attributes.getLocalName(i), attributes.getValue(i));
		}

		Element<?> element = null;
		if (contentStack.isEmpty()) {
			firstElement = false;
			element = new ComplexElementImpl(type, null, values);
		}
		else {
			element = contentStack.peek().getType().get(localName);
		}
		// it does not exist
		if (element == null) {
			// check if there is an xsd:any
			element = contentStack.peek().getType().get(NameProperty.ANY);
			if (element == null) {
				if (!ignoreUndefined)
					throw new SAXException("The element " + localName + " is not expected at this position");
				else {
					// set isNil back to false, otherwise an ignored null field will keep isNil on true
					isNil = false;
					ignoreCounter++;
				}
			}
			else {
				anyStack.push(getCurrentPath() + "/" + localName);
				// TODO: this means we currently only support complex types in an any field, a simple string won't be parsed successfully!
				// how to determine that it's a text field...?
				// the type is an empty object type
				// if there is no actual type (no xsi), just interpret as string
				element = new DynamicElement(element, actualType == null ? new be.nabu.libs.types.simple.String() : new BeanType<Object>(Object.class), localName);
			}
		}
		if (ignoreCounter == 0) {
			elementStack.push(contentStack.isEmpty() ? null : element);
			Type intendedType = elementStack.peek() == null ? type : elementStack.peek().getType();
			if (contentStack.isEmpty() && !element.getName().equals(localName))
				throw new SAXException("The root tag " + localName + " does not match the expected name: " + element.getName());
			if (actualType != null) {
				// intended type can be null if no complex type is given
				if (intendedType != null && !TypeUtils.isSubset(new BaseTypeInstance(actualType), new BaseTypeInstance(intendedType)) && TypeUtils.getUpcastPath(actualType, intendedType).isEmpty()) {
					if (!allowSuperTypes || (!TypeUtils.isSubset(new BaseTypeInstance(intendedType), new BaseTypeInstance(actualType)) && TypeUtils.getUpcastPath(intendedType, actualType).isEmpty())) {
						throw new SAXException("The xsi type " + actualType + " is not compatible with the defined type " + intendedType);
					}
					else {
						intendedType = actualType;
					}
				}
				else
					intendedType = actualType;
			}
			if (intendedType instanceof ComplexType) {
				isComplexType = true;
				// complex types can also be simple
				isSimpleType = intendedType instanceof SimpleType;
				
				ComplexType complexType = (ComplexType) intendedType;
				// note: it is possible (per the spec) to have other attributes even is xsi:nil is set. you simply can not have content (text or elements)
				contentStack.push(isNil && elementAttributes.isEmpty() ? null : complexType.newInstance());
				// set it as the main instance
				if (contentStack.size() == 1)
					instance = contentStack.peek();
				
				pathStack.push(localName);
				boolean foundCollection = false;
				for (String key : elementAttributes.keySet()) {
					if ("collectionIndex".equals(key)) {
						foundCollection = true;
						collectionIndexes.push(elementAttributes.get(key));
						continue;
					}
					key = preprocess(key);
					Element<?> attributeElement = complexType.get(key);
					if (attributeElement == null) {
						if (ignoreUndefined) {
							continue;
						}
						else {
							throw new SAXException("The attribute " + key + " is not supported at this location");
						}
					}
					Object unmarshalled;
					if (!(attributeElement.getType() instanceof Unmarshallable)) {
						// if it's an inputstream, assume base64 encoding
						if (attributeElement.getType() instanceof SimpleType && InputStream.class.isAssignableFrom(((SimpleType) attributeElement.getType()).getInstanceClass())) {
							try {
								unmarshalled = IOUtils.toInputStream(TranscoderUtils.transcodeBytes(IOUtils.wrap(elementAttributes.get(key).getBytes("ASCII"), true), new Base64Decoder()), true);
							}
							catch (UnsupportedEncodingException e) {
								throw new RuntimeException(e);
							}
							catch (IOException e) {
								throw new RuntimeException(e);
							}
						}
						else {
							throw new SAXException("Can not unmarshal the attribute " + key + ", the type " + attributeElement.getType() + " is not unmarshallable");
						}
					}
					else {
						try {
							unmarshalled = ((Unmarshallable<?>) attributeElement.getType()).unmarshal(elementAttributes.get(key), attributeElement.getProperties());
						}
						catch (RuntimeException e) {
							throw new RuntimeException("Could not parse attribute: " + key, e);
						}
					}
					contentStack.peek().set("@" + key, unmarshalled);
				}
				if (!foundCollection) {
					collectionIndexes.push(null);
				}
				// if it belongs in a window, store the starting offset so we can use it when the element ends
				Window window = getWindow();
				if (window != null)
					windowOffsets.put(window, offset);

			}
			else if (intendedType instanceof SimpleType) {
				isSimpleType = true;
				isComplexType = false;
			}
			else
				throw new SAXException("Expecting either a complex or a simple type but " + intendedType + " is neither");
		}
		// reset content if we start a new element as well, otherwise we get trailing content between last closing tag and this opening tag (mostly whitespace...)
		this.content = null;
	}
	
	private String getCurrentPath() {
		if (pathStack.size() == 0)
			return pathPrefix + "/";
		else {
			String path = pathPrefix;
			for (String element : pathStack)
				path += "/" + element;
			return path;
		}
	}
	
	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		// only retain the content if we are interested in it
		if (ignoreCounter == 0) {
			if (content == null) {
				content = new StringWriter();
			}
			content.append(new String(ch, start, length));
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (ignoreCounter >  0) {
			ignoreCounter--;
			return;
		}
		if (localName.isEmpty()) {
			localName = qName;
		}
		localName = preprocess(localName);
		
		boolean isAny = false;
		if (!anyStack.isEmpty()) {
			String pathToCheck = isSimpleType 
				? getCurrentPath() + "/" + localName
				: getCurrentPath();
			if (anyStack.peek().equals(pathToCheck)) {
				isAny = true;
				anyStack.pop();
			}
		}
		
		// this is the end of a simple type
		if (isSimpleType) {
			String content = !isNil && this.content != null ? this.content.toString() : null;
			if (trimContent && content != null) {
				content = content.trim();
			}
			Object convertedContent = null;
			if (content != null && content.length() > 0) {
				Type typeToCheck = elementStack.peek().getType();
				while (typeToCheck != null && !(typeToCheck instanceof Unmarshallable)) {
					typeToCheck = typeToCheck.getSuperType();
				}
				// no unmarshallable type found in the super types
				if (typeToCheck == null) {
					if (elementStack.peek().getType() instanceof SimpleType && (InputStream.class.isAssignableFrom(((SimpleType) elementStack.peek().getType()).getInstanceClass()) || byte[].class.isAssignableFrom(((SimpleType) elementStack.peek().getType()).getInstanceClass()))) {
						try {
							ReadableContainer<ByteBuffer> transcodedBytes = TranscoderUtils.transcodeBytes(IOUtils.wrap(content.getBytes("ASCII"), true), new Base64Decoder());
							convertedContent = InputStream.class.isAssignableFrom(((SimpleType) elementStack.peek().getType()).getInstanceClass()) 
								? IOUtils.toInputStream(transcodedBytes, true)
								: IOUtils.toBytes(transcodedBytes);
						}
						catch (UnsupportedEncodingException e) {
							throw new RuntimeException(e);
						}
						catch (IOException e) {
							throw new RuntimeException(e);
						}
					}
					else {
						throw new SAXException("The element '" + localName + "' in " + getCurrentPath() + " can not be unmarshalled");
					}
				}
				else {
					convertedContent = ((Unmarshallable<?>) typeToCheck).unmarshal(content, elementStack.peek().getProperties());
				}
			}
			if (elementStack.peek().getType().isList(elementStack.peek().getProperties())) {
				List<?> list = (List<?>) contentStack.peek().get(localName);
				if (list == null)
					contentStack.peek().set(localName + "[0]", convertedContent);
				else
					contentStack.peek().set(localName + "[" + list.size() + "]", convertedContent);
			}
			else if (isAny) {
				contentStack.peek().set(NameProperty.ANY + "[" + localName + "]", convertedContent);
			}
			else {
				contentStack.peek().set(localName, convertedContent);
			}
			// reset simple type so the next "stop" doesn't think it's a simple type
			isSimpleType = false;
			// if it is a simple complex type, pop some stacks
			if (isComplexType) {
				contentStack.pop();
				pathStack.pop();
			}
		}
		// this is the end of a complex type
		else {
			Window activeWindow = getWindow();
			ComplexContent currentInstance = contentStack.pop();

			String onStack = pathStack.pop();
			if (!onStack.equals(localName))
				throw new SAXException("Closing tag " + localName + " did not have an opening tag, found " + onStack);

			// always pop because you always push
			Object index = collectionIndexes.pop();
			// append the complex content to the current path, beware of lists
			if (elementStack.peek() != null && elementStack.peek().getType().isList(elementStack.peek().getProperties())) {
				Object currentObject = contentStack.peek().get(localName);
				
				if (activeWindow != null && offset < 0) {
					throw new SAXException("Windowing was activated, but offsets are missing, please use the StAX parser for windowed parsing");
				}
				
				if (index == null) {
					if (currentObject != null) {
						CollectionHandlerProvider provider = collectionHandler.getHandler(currentObject.getClass());
						index = provider.getAsCollection(currentObject).size();
					}
					else {
						index = 0;
					}
				}
				
				// no list yet and its a window
				// explicitly set a windowed list
				if (index instanceof Integer && activeWindow != null) {
					WindowedList windowedList = null;

					if (currentObject == null) {
						// take a snapshotted copy of the namespaces
						PartialXMLUnmarshaller unmarshaller = new PartialXMLUnmarshaller(new HashMap<String, String>(namespaces), (ComplexType) currentInstance.getType(), charset, windows, elementStack.peek().getProperties());
						unmarshaller.setCamelCaseDashes(camelCaseDashes);
						unmarshaller.setCamelCaseUnderscores(camelCaseUnderscores);
						unmarshaller.setIgnoreUndefined(ignoreUndefined);
						unmarshaller.setTrimContent(trimContent);
						windowedList = new WindowedList(resource, activeWindow, unmarshaller);
						contentStack.peek().set(localName, windowedList);
					}
					else if (currentObject instanceof WindowedList) {
						windowedList = (WindowedList) currentObject;
					}
					else {
						throw new IllegalArgumentException("The collection already exists and is not windowed");
					}
					// always register the offset
					windowedList.setOffset((Integer) index, windowOffsets.get(activeWindow));
					
					if ((Integer) index < activeWindow.getSize()) {
						contentStack.peek().set(localName + "[" + index + "]", currentInstance);
					}
				}					
				else {
					contentStack.peek().set(localName + "[" + index + "]", currentInstance);
				}
			}
			else if (contentStack.size() >= 1) {
				if (isAny) {
					contentStack.peek().set(NameProperty.ANY + "[" + localName + "]", currentInstance);	
				}
				else {
					contentStack.peek().set(localName, currentInstance);
				}
			}
		}
		// reset isNil
		isNil = false;
		elementStack.pop();
		this.content = null;
	}
	
	public boolean isDone() {
		return !firstElement && contentStack.isEmpty();
	}

	public ComplexContent getInstance() {
		return instance;
	}
	
	public ComplexType getType() {
		return type;
	}

	public int getOffset() {
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	public XMLBinding getBinding() {
		return binding;
	}

	public void setBinding(XMLBinding binding) {
		this.binding = binding;
	}

	public boolean isAllowExtensions() {
		return allowExtensions;
	}

	public void setAllowExtensions(boolean allowExtensions) {
		this.allowExtensions = allowExtensions;
	}

	public boolean isIgnoreUndefined() {
		return ignoreUndefined;
	}

	public void setIgnoreUndefined(boolean ignoreUndefined) {
		this.ignoreUndefined = ignoreUndefined;
	}

	public TypeRegistry getRegistry() {
		return registry;
	}

	public void setRegistry(TypeRegistry registry) {
		this.registry = registry;
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

	public boolean isAllowSuperTypes() {
		return allowSuperTypes;
	}

	public void setAllowSuperTypes(boolean allowSuperTypes) {
		this.allowSuperTypes = allowSuperTypes;
	}
	
}