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
