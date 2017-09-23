package winedunk.pf.helpers;

public enum TblWineFields {

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
	WINE_TYPE("wineType"),
	WINE_GRAPEVARIETY("grapeVariety");
	
	private final String selectedField;

	private TblWineFields(String field) {
		this.selectedField = field;
	}
	
	@Override
	public String toString() {
		return selectedField;
	}
}
