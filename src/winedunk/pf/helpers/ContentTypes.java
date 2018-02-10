package winedunk.pf.helpers;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ContentTypes {

	HTML,
	JSON,
	XML;
	
	@Override
	@JsonValue
	public String toString() {
		return super.toString();
	}
}
