package winedunk.pf.helpers;

public enum tblWineFields {

	NAME("name"),
	COUNTRY("countryId"),
	REGION("regionId"),
	APPELLATION("appellationId"),
	WINERY("wineryId"),
	COLOUR("colourId"),
	CLOSURE("closureId"),
	VINTAGE("vintage"),
	BOTTLE_SIZE("bottleSize"),
	ABV("abv"),
	DESCRIPTION("defaultDescription"),
	GTIN("gtin"),
	WINE_TYPE("wineType");
	
	private final String selectedField;

	private tblWineFields(String field) {
		this.selectedField = field;
	}
	
	@Override
	public String toString() {
		return selectedField;
	}
}
