package be.nabu.libs.types.binding.xml;

public interface AttributeFilter {
	public boolean accept(Object content, String attribute, String value);
}