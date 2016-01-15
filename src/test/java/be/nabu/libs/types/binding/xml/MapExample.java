package be.nabu.libs.types.binding.xml;

import java.util.HashMap;
import java.util.Map;

public class MapExample {
	
	private Map<String, MapEntry> entries = new HashMap<String, MapEntry>();
	private MapEntry single;
	
	public MapExample() { single = new MapEntry("single"); }
	public MapExample(String...values) {
		this();
		for (String value : values) {
			entries.put(value, new MapEntry(value));
		}
	}
	
	public MapEntry getSingle() {
		return single;
	}
	public void setSingle(MapEntry single) {
		this.single = single;
	}
	public Map<String, MapEntry> getEntries() {
		return entries;
	}
	public void setEntries(Map<String, MapEntry> entries) {
		this.entries = entries;
	}

	public static class MapEntry {
		private String value;

		public MapEntry() {}
		public MapEntry(String value) {
			this.value = value;
		}
		
		public String getValue() {
			return value;
		}
		public void setValue(String value) {
			this.value = value;
		}
		
		@Override
		public boolean equals(Object object) {
			return object instanceof MapEntry && ((MapEntry) object).value.equals(value);
		}
	}
}
