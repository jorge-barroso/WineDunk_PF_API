package winedunk.pf.helpers;

public enum NoDataFieldsValues {
	NO_COUNTRY("No country"),
	NO_REGION("No region"),
	NO_APPELLATION("No appellation"),
	NO_WINERY("No winery"),
	NO_COLOUR("No colour"),
	NO_CLOSURE("No closure"),
	NO_VARIETY("No variety");

	private final String selectedField;

	private NoDataFieldsValues(String selectedField) {
		this.selectedField = selectedField;
	}

	@Override
	public String toString() {
		return selectedField;
	}
}
