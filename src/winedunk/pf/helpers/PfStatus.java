package winedunk.pf.helpers;

public enum PfStatus {

	OK("Ok"),
	PROCESSING("Processing"),
	ERROR("Error"),
	NEW("New");

	private final String selectedField;

	private PfStatus(String selectedField) {
		this.selectedField = selectedField;
	}

	@Override
	public String toString() {
		return this.selectedField;
	}
}