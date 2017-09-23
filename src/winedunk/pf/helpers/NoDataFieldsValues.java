package winedunk.pf.helpers;

public enum NoDataFieldsValues {
	NO_COUNTRY("No Country"),
	NO_REGION("No Region"),
	NO_APPELLATION("No Appellation"),
	NO_WINERY("No Winery"),
	NO_COLOUR("No Colour"),
	NO_CLOSURE("No Closure"),
	NO_VARIETY("No Variety"),
	NO_WINETYPE("No WineType");

	private final String selectedField;

	private NoDataFieldsValues(String selectedField) {
		this.selectedField = selectedField;
	}

	@Override
	public String toString() {
		return selectedField;
	}
}
