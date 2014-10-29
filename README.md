# XML Binding

The XML binding for types can be likened a bit to JAXB but in general goes a bit further, a few highlights:

- **Large files**: supported during unmarshalling (see types-binding-api). Uses SAX for non-large files and wraps a woodstox-based StAX parser around it for large data. Woodstox is the only parser (that I tried) that gives accurate offset information and allows partial parses to be performed. 
	- The only downside is that the StAX API uses an **integer** to indicate the offset whereas the types-binding framework uses a long. The integer currently limits how big of an XML you can parse. It is pretty vague to give an exact number as the offset is in characters (so it depends on your encoding) and only needed for repeated elements (so basically your footer can be in a part that overflows the integer position)
	- Note that woodstox drags in a dependency to an old stax api which needs to be ignored
- **XSI Type**: unlike JAXB, the xml marshaller will use xsi:type by default if it detects that the runtime type is an extension of the definition type instead of the actual definition type. The unmarshaller can then reinterpret this xsi:type to unmarshal the file with the correct types.
- **Namespaces**: There is a heavy focus on being able to manipulate namespaces during marshalling as many systems don't work well with them (so you can force a default namespace or prohibit one) or require fixed prefixes (you can set these)
- **XSI**: you can disable xsi features (mostly nil & type) if necessary
- **Misc**: You can force optional empty field generation and override the default "qualified" property available in the type definition
