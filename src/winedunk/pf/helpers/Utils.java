package winedunk.pf.helpers;

public class Utils {

	public static <T extends Object> T getOrDefault(T mainValue, T defaultValue)
	{
		return mainValue==null ? defaultValue : mainValue;
	}
}
