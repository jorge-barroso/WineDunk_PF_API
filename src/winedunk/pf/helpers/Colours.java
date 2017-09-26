package winedunk.pf.helpers;

public enum Colours {
	RED("Red"),
	ROSE("Rose"),
	WHITE("White");
	
	private String selectedValue;
	
	private Colours(String selectedValue)
	{
		this.selectedValue = selectedValue;
	}

	public String toString()
	{
		return this.selectedValue;
	}
}
