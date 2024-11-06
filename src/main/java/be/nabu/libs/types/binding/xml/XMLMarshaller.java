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

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.BaseTypeInstance;
import be.nabu.libs.types.CollectionHandlerFactory;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.DefinedTypeResolverFactory;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.Attribute;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedSimpleType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.DefinedTypeResolver;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.MarshalException;
import be.nabu.libs.types.api.Marshallable;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.api.TypeInstance;
import be.nabu.libs.types.base.DynamicElement;
import be.nabu.libs.types.java.BeanType;
import be.nabu.libs.types.properties.AttributeQualifiedDefaultProperty;
import be.nabu.libs.types.properties.ElementQualifiedDefaultProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.properties.NameProperty;
import be.nabu.libs.types.properties.NamespaceProperty;
import be.nabu.libs.types.properties.NillableProperty;
import be.nabu.libs.types.properties.QualifiedProperty;
import be.nabu.libs.types.resultset.ResultSetWithType;
import be.nabu.libs.types.resultset.ResultSetWithTypeCollectionHandler;
import be.nabu.utils.codec.TranscoderUtils;
import be.nabu.utils.codec.impl.Base64Encoder;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;

/**
 * This class is not threadsafe!
 */
public class XMLMarshaller {
	
	public static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";
	
	/**
	 * Unless explicitly set, it will use the settings stored in the type
	 */
	private Boolean attributeQualified;
	private Boolean elementQualified;
	
	private TypeInstance typeInstance;
	
	private Set<String> definedNamespaces;
	
	private DefinedTypeResolver typeResolver = DefinedTypeResolverFactory.getInstance().getResolver();
	
	private String prefix = "tns";
	
	/**
	 * Allows you to set an xsi type on the root element (requires allowXSI true)
	 * This can make it easier to unmarshal the XML later on
	 */
	private String xsiType;
	/**
	 * This allows usage of the default namespace
	 * This means it will be used for the topmost namespace and all other namespaces will be prefixed
	 * This generates clean xml
	 * If you turn it off, all namespaces are prefixed which may be necessary if the client does not understand the default namespace
	 */
	private boolean allowDefaultNamespace = true;
	
	/**
	 * This means the default namespace is redefined for each element that has a different namespace
	 * This can be useful when interacting with clients that don't understand namespaces and/or prefixes too well
	 */
	private boolean forceDefaultNamespace = false;
	
	/**
	 * If the forcedefaultnamespace is turned off and this is turned on, all namespaces used in the complex type are defined in the root
	 * This will prevent redefinition of namespaces throughout the xml but may define namespaces that are not actually used (optional elements)
	 */
	private boolean forceRootNamespaceDefinition = true;
	
	/**
	 * Whether it should use namespaces at all
	 * If this is turned of, all other namespace-related options are moot
	 */
	private boolean namespaceAware = true;
	
	/**
	 * If a field is optional and it does not contain any content it is by default not shown
	 * Toggle this to force tag generation
	 */
	private boolean forceOptionalEmptyFields = false;
	
	/**
	 * Should the marshaller pretty print?
	 */
	private boolean prettyPrint = true;
	
	/**
	 * In pretty print mode, should multiline the attributes?
	 * Can be useful when performing line diffs
	 */
	private boolean multilineAttributes = false;
	
	/**
	 * Whether we want to allow multiline in attributes
	 * If so, we need to encode it, otherwise it will normalized to a space when parsing
	 */
	private boolean multilineInAttributes = false;
	/**
	 * Whether or not individual elements can override the qualified setting
	 */
	private boolean allowQualifiedOverride = false;
	
	/**
	 * Namespaces that are already mapped to prefixes
	 * This allows you to generate xml with specific prefixes
	 */
	private Map<String, String> namespaces = new HashMap<String, String>();
	
	/**
	 * Whether or not we want to use xsi features
	 */
	private boolean allowXSI = true;
	
	/**
	 * Whether or not we want to marshal streams as (base64 encoded) bytes
	 */
	private boolean marshalStreams = true;
	
	/**
	 * Used to generate new prefixes
	 */
	private int namespaceCounter = 1;
	/**
	 * We only check if the $value field is available to determine a complex type is also a simple type
	 * This means you can never use a field like that as an actual child unless you turn this off
	 * At that point the complex type actually has to be instanceof simple type
	 */
	private boolean validateSimpleComplexByValue = true;
	
	public XMLMarshaller(TypeInstance typeInstance) {
		this.typeInstance = typeInstance;
	}
	
	public void marshal(OutputStream output, Charset charset, ComplexContent content) throws IOException {
		marshal(new OutputStreamWriter(output, charset), content);
	}
	
	public void marshal(Writer writer, ComplexContent content) throws IOException {
		BufferedWriter bufferedWriter = new BufferedWriter(writer);
		marshal(bufferedWriter, content, typeInstance, namespaces, true, null, 0, isAttributeQualified(), isElementQualified(), null, false, false);
		bufferedWriter.flush();
	}
	
	protected boolean isAttributeQualified() {
		if (attributeQualified == null)
			attributeQualified = ValueUtils.getValue(new AttributeQualifiedDefaultProperty(), typeInstance.getProperties());
		return attributeQualified;
	}
	
	protected boolean isElementQualified() {
		if (elementQualified == null)
			elementQualified = ValueUtils.getValue(new ElementQualifiedDefaultProperty(), typeInstance.getProperties());
		return elementQualified;
	}
	
	public void setDefaultNamespace(String defaultNamespace) {
		setAllowDefaultNamespace(true);
		namespaces.put(defaultNamespace, null);
	}
	
	public void setPrefix(String namespace, String prefix) {
		namespaces.put(namespace, prefix);
	}
	
	/**
	 * 
	 * @param writer
	 * @param content
	 * @param typeInstance
	 * @param namespaces Maps namespace > prefix, if prefix is null, it is the default namespace
	 * @throws IOException
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void marshal(Writer writer, Object content, TypeInstance typeInstance, Map<String, String> namespaces, boolean isRoot, Map<String, String> additionalAttributes, int depth, boolean attributeQualified, boolean elementQualified, String parentNamespace, boolean isAny, boolean isFormQualified) throws IOException {
		// wrap around the initial map so it does not modify the original map (basically you don't want the newly defined namespaces to exist outside of their scope)
		namespaces = new HashMap<String, String>(namespaces);
		
		boolean newAttributeQualified = attributeQualified;
		boolean newElementQualified = elementQualified;
		if (allowQualifiedOverride) {
			// first check if we have a QualifiedProperty setting, this is directly set on the element (through use of the 'form' attributes in xsd)
			Value<Boolean> qualifiedProperty = typeInstance.getProperty(QualifiedProperty.getInstance());
			// if we have a specific setting, that wins
			if (qualifiedProperty != null && qualifiedProperty.getValue() != null) {
				newElementQualified = qualifiedProperty.getValue();
				isFormQualified = true;
			}
			Value<Boolean> attributeQualifiedProperty = typeInstance.getProperty(AttributeQualifiedDefaultProperty.getInstance());
			if (attributeQualifiedProperty != null) {
				newAttributeQualified = attributeQualifiedProperty.getValue();
				// attributes are considered children, we must change this immediately
				attributeQualified = newAttributeQualified;
			}
			// if we did not find an explicit qualified indicator (so far), use the default one
			if (!isFormQualified) {
				Value<Boolean> elementQualifiedProperty = typeInstance.getProperty(ElementQualifiedDefaultProperty.getInstance());
				if (elementQualifiedProperty != null) {
					newElementQualified = elementQualifiedProperty.getValue();
				}
			}
		}
		
		String elementName = ValueUtils.getValue(NameProperty.getInstance(), typeInstance.getProperties());
		if (elementName == null) {
			elementName = typeInstance.getType().getName(typeInstance.getProperties());
		}
		
		if (elementName == null) {
			throw new IllegalArgumentException("Could not find element name for: " + typeInstance);
		}

		if (elementName.equals(NameProperty.ANY)) {
			// for an any element with no content > write nothing
			// otherwise it MUST be a collection
			if (content != null) {
				CollectionHandlerProvider collectionHandler = CollectionHandlerFactory.getInstance().getHandler().getHandler(content.getClass());
				for (Object index : collectionHandler.getIndexes(content)) {
					Object child = collectionHandler.get(content, index);
					Type type = child == null ? typeResolver.resolve(String.class.getName()) : typeResolver.resolve(child.getClass().getName());
					// if it's a complex type, we want xsi:type to kick in to annotate what type it is
					if (type instanceof ComplexType) {
						type = new BeanType<Object>(Object.class);
					}
					DynamicElement dynamicType = new DynamicElement((Element<?>) typeInstance, type, index.toString(), typeInstance.getProperties());
					marshal(writer, child, dynamicType, namespaces, isRoot, null, depth, newAttributeQualified, newElementQualified, parentNamespace, true, isFormQualified);
				}
			}
		}
		else {
			String elementNamespace = ValueUtils.getValue(NamespaceProperty.getInstance(), typeInstance.getProperties());
			// if we can not find a namespace, inherit from the parent
			if (elementNamespace == null) {
				elementNamespace = parentNamespace;
			}
			if (elementNamespace == null) {
				elementNamespace = typeInstance.getType().getNamespace(typeInstance.getProperties());
			}
			// ignore the xml schema namespace
			if (elementNamespace != null && elementNamespace.equals(Type.XML_SCHEMA)) {
				elementNamespace = null;
			}
			
			if (prettyPrint && !isRoot) {
				writer.append("\n");
				for (int i = 0; i < depth; i++) {
					writer.append("\t");
				}
			}
			writer.append("<");
			boolean isNamespaceDefined = false;
	
			// we need a namespace for this element
			if (namespaceAware && elementNamespace != null && (elementQualified || isRoot)) {
				// this namespace has not yet been defined
				if (!namespaces.containsKey(elementNamespace)) {
					// if there is no default namespace yet and we allow using it, use the default one
					if (allowDefaultNamespace && !namespaces.values().contains(null))
						namespaces.put(elementNamespace, null);
					// we want to force the default namespace
					else if (forceDefaultNamespace) {
						// this means if there is already a default namespace, we have to unset it
						if (namespaces.values().contains(null)) {
							for (String key : namespaces.keySet()) {
								if (namespaces.get(key) == null)
									namespaces.remove(key);
							}
						}
						namespaces.put(elementNamespace, null);
					}
					// otherwise generate a new prefix
					else
						namespaces.put(elementNamespace, prefix + namespaceCounter++);
				}
				// mark it as already defined
				else
					isNamespaceDefined = true;
	
				// add the prefix if it is not the default
				if (namespaces.get(elementNamespace) != null)
					writer.append(namespaces.get(elementNamespace)).append(":");
			}
			writer.append(elementName);
			
			// if the namespace was not already defined, define it, ignore ##default namespace
			// if we are in the root and the root namespace was predefined (before marshalling), we still have to print it
			if (namespaceAware && elementNamespace != null && (!isNamespaceDefined || isRoot) && (elementQualified || isRoot) && !elementNamespace.equals("##default")) {
				writer.append(" xmlns");
				if (namespaces.get(elementNamespace) != null)
					writer.append(":").append(namespaces.get(elementNamespace));
				writer.append("=\"").append(elementNamespace).append("\"");
			}
			
			// if we want to define all the namespaces, check if this still needs to be done
			// either the elements or the attributes must be qualified
			if (namespaceAware && !forceDefaultNamespace && forceRootNamespaceDefinition && isRoot && (elementQualified || attributeQualified)) {
				for (String possibleNamespace : getDefinedNamespaces()) {
					if (!namespaces.containsKey(possibleNamespace))
						namespaces.put(possibleNamespace, prefix + namespaceCounter++);
	
					// the current namespace of the root element has already been defined (see above) so only define the rest
					// it is theoretically possible to define the default namespace before running this so it might differ from the root namespace
					if (!possibleNamespace.equals(elementNamespace)) {
						writer.append(" xmlns");
						if (namespaces.get(possibleNamespace) != null)
							writer.append(":").append(namespaces.get(possibleNamespace));
						writer.append("=\"").append(possibleNamespace).append("\"");
					}
				}
			}
	
			// if the xsi namespace has not been defined, define it
			if (!namespaces.containsKey(XSI) && isAllowXSI()) {
				namespaces.put(XSI, "xsi");
				writer.append(" xmlns:xsi=\"").append(XSI).append("\"");
			}
			
			if (additionalAttributes != null) {
				for (String key : additionalAttributes.keySet()) {
					if (prettyPrint && multilineAttributes) {
						writer.append("\n");
						for (int i = 0; i < depth + 2; i++) {
							writer.append("\t");
						}
					}
					else {
						writer.append(" ");
					}
					writer.append(key).append("=\"").append(encodeAttribute(additionalAttributes.get(key))).append("\"");
				}
			}

			if (content != null && typeInstance.getType() instanceof BeanType && ((BeanType) typeInstance.getType()).getBeanClass().equals(Object.class)) {
				DefinedSimpleType<? extends Object> wrap = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(content.getClass());
				if (wrap != null) {
					typeInstance = new BaseTypeInstance(wrap, typeInstance.getProperties());
				}
				else {
					ComplexContent complexContent = content instanceof ComplexContent ? (ComplexContent) content : ComplexContentWrapperFactory.getInstance().getWrapper().wrap(content); 
					typeInstance = new BaseTypeInstance(complexContent.getType(), typeInstance.getProperties());
				}
				// if we have a java lang object, we want to inject the xsi:type so we can properly undo it at the other end
				isAny = true;
			}

			if (typeInstance.getType() instanceof ComplexType) {
				ComplexType complexType = (ComplexType) typeInstance.getType();
				ComplexContent complexContent = content == null || content instanceof ComplexContent ? (ComplexContent) content : ComplexContentWrapperFactory.getInstance().getWrapper().wrap(content);
				// we need to write all the attributes into the element tag, we need to check the definition to see which elements are attributes
				// additionally while we are looping we can check if there is _any_ content at all and generate a self closing tag if not
				boolean hasContent = false;
				// if there is no complex content, just skip this step and go straight to the empty generation
				if (complexContent != null) {
					// if you allow xsi and the complex content is actually a defined extension of the complex type, add it
					// it doesn't specifically check for extension because this should be enforced by the types, not the marshaller
					// for any, also put the xsi:type, otherwise the other end doesn't know which type you mean
					if (allowXSI && (!complexContent.getType().equals(complexType) || isAny) && complexContent.getType() instanceof DefinedType) {
						// TODO: should use namespace prefix to allow other tools to also unmarshal it
						writer.append(" xsi:type=\"" + ((DefinedType) complexContent.getType()).getId() + "\"");
						complexType = complexContent.getType();
					}
					else if (allowXSI && isRoot && xsiType != null) {
						writer.append(" xsi:type=\"" + xsiType + "\"");
					}
					for (Element<?> child : TypeUtils.getAllChildren(complexType)) {
						if (child instanceof Attribute || child.getName().startsWith("@")) {
							Object value = complexContent.get(child.getName());
							// depending on the complex content used, attributes may reside in the @ annotated field
							// e.g. we often wrap xml schema (that uses Attribute at definition time without a @) in a structure instance (that uses @ at runtime)
							if (value == null && !child.getName().startsWith("@")) {
								value = complexContent.get("@" + child.getName());
							}
							if (value != null) {
								SimpleType<?> type = (SimpleType<?>) child.getType();
								if (!(type instanceof Marshallable)) {
									throw new MarshalException("The attribute " + type.getName() + " can not be marshalled");
								}
								String marshalledValue = ((Marshallable) type).marshal(value, child.getProperties());
								if (prettyPrint && multilineAttributes) {
									writer.append("\n");
									for (int i = 0; i < depth + 2; i++) {
										writer.append("\t");
									}
								}
								else {
									writer.append(" ");
								}
								if (namespaceAware && child.getNamespace() != null && attributeQualified) {
									if (!namespaces.containsKey(child.getNamespace())) {
										namespaces.put(child.getNamespace(), prefix + namespaceCounter++);
										writer.append("xmlns:").append(namespaces.get(child.getNamespace())).append("=\"").append(child.getNamespace()).append("\" ");
									}
									if (namespaces.get(child.getNamespace()) != null)
										writer.append(namespaces.get(child.getNamespace())).append(":");
								}
								String name = child.getName();
								if (name.startsWith("@")) {
									name = name.substring(1);
								}
								writer.append(name).append("=\"").append(encodeAttribute(marshalledValue)).append("\"");
							}
						}
						else {
							if (!hasContent)
								hasContent = complexContent.get(child.getName()) != null;
						}
					}
				}
				// if we have a simple complex type and it has content, make sure we marshal it
				hasContent |= (validateSimpleComplexByValue || complexType instanceof SimpleType) && complexContent != null && complexType.get(ComplexType.SIMPLE_TYPE_VALUE) != null && complexContent.get(ComplexType.SIMPLE_TYPE_VALUE) != null;
				if (hasContent) {
					writer.append(">");
					// it only has the attributes and a value element which is filled in (hasContent is true), marshal and set
					if (complexType instanceof SimpleType || (validateSimpleComplexByValue && complexType.get(ComplexType.SIMPLE_TYPE_VALUE) != null)) {
						Element<?> valueElement = complexType.get(ComplexType.SIMPLE_TYPE_VALUE);
						if (!(valueElement.getType() instanceof Marshallable))
							throw new MarshalException("The simple value for element " + valueElement.getName() + " can not be marshalled");
						Object value = complexContent.get(ComplexType.SIMPLE_TYPE_VALUE);
						String marshalledValue = ((Marshallable) valueElement.getType()).marshal(value, typeInstance.getProperties());
						writer.append(encode(marshalledValue));
					}
					// just loop over the non-attribute children
					else {
						for (Element<?> child : TypeUtils.getAllChildren(complexType)) {
							if (!(child instanceof Attribute) && !child.getName().startsWith("@")) {
								Object value = complexContent.get(child.getName());
								// recurse
								if (value != null || ValueUtils.getValue(MinOccursProperty.getInstance(), child.getProperties()) > 0 || forceOptionalEmptyFields) {
									if (value instanceof Collection) {
										for (Object childValue : (Collection) value)
											marshal(writer, childValue, child, namespaces, false, null, depth + 1, newAttributeQualified, newElementQualified, elementNamespace, false, isFormQualified);	
									}
									else if (value instanceof Object[]) {
										for (Object childValue : (Object[]) value)
											marshal(writer, childValue, child, namespaces, false, null, depth + 1, newAttributeQualified, newElementQualified, elementNamespace, false, isFormQualified);
									}
									else if (value instanceof Iterable) {
										for (Object childValue : (Iterable) value)
											marshal(writer, childValue, child, namespaces, false, null, depth + 1, newAttributeQualified, newElementQualified, elementNamespace, false, isFormQualified);
									}
									// xsd:any has special handling, don't capture it in the map step
									// if the map has been interpreted into a type (e.g. through map type) so it is not exposed as a list, don't use the collection approach
									else if (value instanceof Map && !NameProperty.ANY.equals(ValueUtils.getValue(NameProperty.getInstance(), child.getProperties())) && child.getType().isList(child.getProperties())) {
										for (Object key : ((Map) value).keySet()) {
											Map<String, String> attributes = new HashMap<String, String>();
											attributes.put("collectionIndex", key.toString());
											marshal(writer, ((Map) value).get(key), child, namespaces, false, attributes, depth + 1, newAttributeQualified, newElementQualified, elementNamespace, false, isFormQualified);
										}
									}
									// should really refactor this to use the generic collection handling but this is _very_ old code
									else if (value instanceof ResultSetWithType) {
										Iterable<?> iterable = new ResultSetWithTypeCollectionHandler().getAsIterable((ResultSetWithType) value);
										for (Object childValue : iterable) {
											marshal(writer, childValue, child, namespaces, false, null, depth + 1, newAttributeQualified, newElementQualified, elementNamespace, false, isFormQualified);
										}
									}
									else {
										marshal(writer, value, child, namespaces, false, null, depth + 1, newAttributeQualified, newElementQualified, elementNamespace, false, isFormQualified);
									}
								}
							}
						}
						if (prettyPrint) {
							writer.append("\n");
							for (int i = 0; i < depth; i++) {
								writer.append("\t");
							}
						}
					}
					writer.append("</");
					if (namespaceAware && elementNamespace != null && namespaces.get(elementNamespace) != null && (elementQualified || isRoot))
						writer.append(namespaces.get(elementNamespace)).append(":");
					writer.append(elementName).append(">");
				}
				else {
					if (allowXSI && ValueUtils.getValue(NillableProperty.getInstance(), typeInstance.getProperties()))
						writer.append(" xsi:nil=\"true\"");
					writer.append("/>");
				}
			}
			else {
				if (allowXSI && isAny && typeInstance.getType() instanceof DefinedType) {
					// TODO: should use namespace prefix to allow other tools to also unmarshal it
					writer.append(" xsi:type=\"" + ((DefinedType) typeInstance.getType()).getId() + "\"");
				}
				if (content == null) {
					// only set an explicit nil if you allow xsi and the property allows for nillable values
					if (allowXSI && ValueUtils.getValue(NillableProperty.getInstance(), typeInstance.getProperties()))
						writer.append(" xsi:nil=\"true\"");
					writer.append("/>");
				}
				else {
					writer.append(">");
					SimpleType<?> simpleType = (SimpleType<?>) typeInstance.getType();
					while (simpleType != null && !(simpleType instanceof Marshallable)) {
						simpleType = (SimpleType<?>) simpleType.getSuperType();
					}
					String marshalledValue;
					if (!(simpleType instanceof Marshallable)) {
						if ((content instanceof InputStream && marshalStreams) || content instanceof byte[]) {
							if (content instanceof byte[]) {
								content = new ByteArrayInputStream((byte[]) content);
							}
							ReadableContainer<ByteBuffer> transcodedBytes = TranscoderUtils.transcodeBytes(IOUtils.wrap((InputStream) content), new Base64Encoder());
							marshalledValue = IOUtils.toString(IOUtils.wrapReadable(transcodedBytes, Charset.forName("ASCII")));
						}
						else {
							throw new MarshalException("The simple value for " + typeInstance + " using type " + simpleType + " can not be marshalled");
						}
					}
					else {
						marshalledValue = ((Marshallable) simpleType).marshal(content, typeInstance.getProperties());
					}
					writer.append(encode(marshalledValue));
					writer.append("</");
					if (elementNamespace != null && namespaces.get(elementNamespace) != null && (elementQualified || isRoot))
						writer.append(namespaces.get(elementNamespace)).append(":");
					writer.append(elementName).append(">");
				}
			}
		}
	}
	
	protected String encodeAttribute(String content) {
		String replace = encode(content).replace("\"", "&quot;");
		if (multilineInAttributes) {
			replace = replace.replace("\n", "&#10;");
		}
		return replace;
	}
	
	protected String encode(String content) {
		return content
			.replace("&", "&amp;")
			.replace(">", "&gt;")
			.replace("<", "&lt;")
			.replace("\u000b", "&#11;")
			.replace("\u000c", "&#12;")
			.replace("\ufffe", "")
			.replace("\uffff", "")
			.replace("\u0000", "");
	}
	
	public boolean isAllowXSI() {
		return allowXSI;
	}

	public void setAllowXSI(boolean allowXSI) {
		this.allowXSI = allowXSI;
	}

	private Set<String> getDefinedNamespaces() {
		if (definedNamespaces == null)
			definedNamespaces = getDefinedNamespaces(typeInstance, new ArrayList<ComplexType>());
		return definedNamespaces;
	}
	private Set<String> getDefinedNamespaces(TypeInstance typeInstance, List<ComplexType> blacklist) {
		Set<String> definedNamespaces = new HashSet<String>();
		String namespace = typeInstance instanceof Element ? ((Element<?>) typeInstance).getNamespace() : typeInstance.getType().getNamespace(typeInstance.getProperties());
		if (namespace != null)
			definedNamespaces.add(namespace);
		if (typeInstance.getType() instanceof ComplexType) {
			ComplexType type = (ComplexType) typeInstance.getType();
			if (!blacklist.contains(type)) {
				blacklist.add(type);
				for (Element<?> child : TypeUtils.getAllChildren(type)) {
					definedNamespaces.addAll(getDefinedNamespaces(child, blacklist));
				}
			}
		}
		return definedNamespaces;
	}

	public Boolean getAttributeQualified() {
		return attributeQualified;
	}

	public void setAttributeQualified(Boolean attributeQualified) {
		this.attributeQualified = attributeQualified;
	}

	public Boolean getElementQualified() {
		return elementQualified;
	}

	public void setElementQualified(Boolean elementQualified) {
		this.elementQualified = elementQualified;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public boolean isAllowDefaultNamespace() {
		return allowDefaultNamespace;
	}

	public void setAllowDefaultNamespace(boolean allowDefaultNamespace) {
		this.allowDefaultNamespace = allowDefaultNamespace;
	}

	public boolean isForceDefaultNamespace() {
		return forceDefaultNamespace;
	}

	public void setForceDefaultNamespace(boolean forceDefaultNamespace) {
		this.forceDefaultNamespace = forceDefaultNamespace;
	}

	public boolean isForceRootNamespaceDefinition() {
		return forceRootNamespaceDefinition;
	}

	public void setForceRootNamespaceDefinition(boolean forceRootNamespaceDefinition) {
		this.forceRootNamespaceDefinition = forceRootNamespaceDefinition;
	}

	public boolean isForceOptionalEmptyFields() {
		return forceOptionalEmptyFields;
	}

	public void setForceOptionalEmptyFields(boolean forceOptionalEmptyFields) {
		this.forceOptionalEmptyFields = forceOptionalEmptyFields;
	}

	public boolean isNamespaceAware() {
		return namespaceAware;
	}

	public void setNamespaceAware(boolean namespaceAware) {
		this.namespaceAware = namespaceAware;
	}

	public boolean isMarshalStreams() {
		return marshalStreams;
	}

	public void setMarshalStreams(boolean marshalStreams) {
		this.marshalStreams = marshalStreams;
	}

	public String getXsiType() {
		return xsiType;
	}

	public void setXsiType(String xsiType) {
		this.xsiType = xsiType;
	}

	public boolean isAllowQualifiedOverride() {
		return allowQualifiedOverride;
	}

	public void setAllowQualifiedOverride(boolean allowQualifiedOverride) {
		this.allowQualifiedOverride = allowQualifiedOverride;
	}

	public boolean isPrettyPrint() {
		return prettyPrint;
	}

	public void setPrettyPrint(boolean prettyPrint) {
		this.prettyPrint = prettyPrint;
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

}
