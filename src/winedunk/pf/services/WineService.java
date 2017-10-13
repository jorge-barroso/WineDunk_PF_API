package winedunk.pf.services;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CharMatcher;
import com.neovisionaries.i18n.CountryCode;

import winedunk.pf.helpers.NoDataFieldsValues;
import winedunk.pf.models.TblPFCountryNameMappingTable;
import winedunk.pf.models.tblAppellations;
import winedunk.pf.models.tblClosures;
import winedunk.pf.models.tblColours;
import winedunk.pf.models.tblCountries;
import winedunk.pf.models.tblRegions;
import winedunk.pf.models.tblWineries;
import winedunk.pf.models.tblWines;

public class WineService {
	private final String apiUrl;
	private final FTPClient ftp = new FTPClient();
	private final RequestsCreator requestsCreator = new RequestsCreator();
	private final ObjectMapper mapper = new ObjectMapper();
	private final Map<String, String> countries = new HashMap<String, String>();
	private final String patternsOpening = "(?i)\\s*(\\/|,|-)?\\s*";
	private final String patternsClosing = "\\s*(\\/|,|-)?\\s*";
	private final Pattern[] vintagePatterns = new Pattern[] {Pattern.compile(patternsOpening+"[0-9]{4}"+patternsClosing), Pattern.compile(patternsOpening+"\\d+\\s*(years|year|yrs|yr)"+patternsClosing), Pattern.compile(patternsOpening+"(,|\\s+)(\')?[0-9]{2}(s|'s)?(\\s+|$)"+patternsClosing)};
	private final Pattern[] bottleSizePatterns = new Pattern[] {Pattern.compile(patternsOpening+"\\d+(\\.\\d+)?cl\\s*(bottles|bottle)?"+patternsClosing), Pattern.compile(patternsOpening+"\\d+(\\.\\d+)?ml\\s*(bottles|bottle)?"+patternsClosing),  Pattern.compile(patternsOpening+"\\d+(\\.\\d+)?\\s*(ltr|lt|litre|l)\\s*(bottles|bottle)?"+patternsClosing), Pattern.compile(patternsOpening+"(\\()?half\\s*(-)?\\s*bottle(\\))?"+patternsClosing)};
	private final Pattern alcoholVolumePattern = Pattern.compile(patternsOpening+"\\d+(\\.\\d)?\\s*(abv|%)"+patternsClosing);
	private final Properties properties;

	/**
	 * 
	 * @param ftp
	 * @param apiUrl
	 * @throws IOException 
	 */
	public WineService(Properties properties)
	{
		this.properties = properties;

		this.apiUrl = this.properties.getProperty("crud.url");

		for(String countryCode : Locale.getISOCountries())
		{
        	this.countries.put(new Locale("", countryCode).getDisplayCountry(), countryCode);
		}	
	}

	/**
	 * 
	 * @param name
	 * @param partnerProduct
	 * @param wineValues
	 * @return
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 */
	public tblWines getInstance(String name, String gtin, String bottleSize, String vintage) throws JsonParseException, JsonMappingException, IOException
	{
		tblWines wine;

		if(gtin!=null)
		{
			wine = this.mapper .readValue(this.requestsCreator.createGetRequest(this.apiUrl, "wines?action=getByGtin&gtin="+gtin), tblWines.class);
		}
		else
		{
			String requestParameters = "action=getByNameBottleAndVintage"
									 + "&name="+URLEncoder.encode(name, "UTF-8")
									 + "&bottleSize="+bottleSize
									 + "&vintage="+vintage;
			wine = this.mapper.readValue(this.requestsCreator.createGetRequest(this.apiUrl, "wines?"+requestParameters), tblWines.class);
		}

		return wine;
	}

	/**
	 * 
	 * @param country
	 * @return
	 */
	public tblCountries getCountry(String countryName)
	{

		System.out.println("Looking for country: "+countryName);
		if(countryName.contains("Product of"));
			countryName = countryName.replace("Product of ", "");

		TblPFCountryNameMappingTable countryNameMapping;
		try {
			final String countryNameMappingJson = this.requestsCreator.createGetRequest(apiUrl, "TblPFCountryNameMappingTableController?action=getByName&name="+countryName);
			countryNameMapping = countryNameMappingJson.isEmpty() ? null : this.mapper.readValue(countryNameMappingJson, TblPFCountryNameMappingTable.class);
		} catch (JsonParseException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return null;
		} catch (JsonMappingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return null;
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return null;
		}

		//If the current wrong name was mapped to a proper name we replace that wrong name with the good one
		if(countryNameMapping!=null)
			countryName = countryNameMapping.getTblCountries().getName();

		tblCountries country;
		try {
			final String countryJson = this.requestsCreator.createGetRequest(this.apiUrl, "countries?action=getByName&name="+countryName);
			country = countryJson.isEmpty() ? null : this.mapper.readValue(countryJson, tblCountries.class);
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		if (country==null)
		{
			final CountryCode cc = countries.get(countryName)==null ? null : CountryCode.getByCode(countries.get(countryName));

			country = new tblCountries();
			if(cc==null)
			{
				if(!countryName.equals(NoDataFieldsValues.NO_COUNTRY.toString()))
				{
					System.out.println("The name of the country "+countryName+" doesn't follow the standards so it couldn't be found");
				}

				country.setName(countryName)
				   .setIsoAlpha2Code("NC")
				   .setIsoAlpha3Code("NC")
				   .setIsoNumericCode(0);
			}
			else
			{
				country.setName(cc.getName())
					   .setIsoAlpha2Code(cc.getAlpha2())
					   .setIsoAlpha3Code(cc.getAlpha3())
					   .setIsoNumericCode(cc.getNumeric());
			}
		}

		return country;
	}

	/**
	 * 
	 * @param region
	 * @return
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 */
	public tblRegions getRegion(String regionName) throws JsonParseException, JsonMappingException, IOException
	{
		final tblRegions region = this.masterGetRegion("regions?action=getByName&name="+regionName);

		if(region.getId()==null)
			region.setName(regionName);

		return region;
	}

	/**
	 * 
	 * @param regionId
	 * @return
	 * @throws JsonParseException
	 * @throws JsonMappingException
	 * @throws IOException
	 */
	public tblRegions getRegion(Integer regionId) throws JsonParseException, JsonMappingException, IOException
	{
		return this.masterGetRegion("regions?action=getRegion&id="+regionId);
	}

	/**
	 * 
	 * @param apiCall
	 * @return
	 * @throws JsonParseException
	 * @throws JsonMappingException
	 * @throws IOException
	 */
	private tblRegions masterGetRegion(String apiCall) throws JsonParseException, JsonMappingException, IOException
	{
		final String regionJson = this.requestsCreator.createGetRequest(this.apiUrl, apiCall);

		return regionJson.isEmpty() ? new tblRegions() : this.mapper.readValue(regionJson, tblRegions.class);
	}
	/**
	 * 
	 * @param appellation
	 * @return
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 */
	public tblAppellations getAppellation(String appellationName) throws JsonParseException, JsonMappingException, IOException
	{
		final tblAppellations appellation = this.getAppellation("getByName", "name="+appellationName);
		if(appellation.getId()==null)
			appellation.setName(appellationName);
		return appellation;
	}
	/**
	 * 
	 * @param appellationId
	 * @return
	 * @throws JsonParseException
	 * @throws JsonMappingException
	 * @throws IOException
	 */
	public tblAppellations getAppellation(Integer appellationId) throws JsonParseException, JsonMappingException, IOException
	{
		return this.getAppellation("getAppellation", "id="+appellationId);
	}
	/**
	 * 
	 * @param action
	 * @param parameters
	 * @return
	 * @throws JsonParseException
	 * @throws JsonMappingException
	 * @throws IOException
	 */
	private tblAppellations getAppellation(String action, String parameters) throws JsonParseException, JsonMappingException, IOException
	{
		final String appellationJson = this.requestsCreator.createGetRequest(apiUrl, "appellations?action="+action+"&"+parameters);

		return appellationJson.isEmpty() ? new tblAppellations() : this.mapper.readValue(appellationJson, tblAppellations.class);
	}

	/**
	 * 
	 * @param winery
	 * @return
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 * @throws IOException 
	 */
	public tblWineries getWinery(String wineryName) throws JsonParseException, JsonMappingException, IOException
	{
		final String wineryJson = this.requestsCreator.createGetRequest(apiUrl, "wineries?action=getByName&name="+wineryName);

		final tblWineries winery = wineryJson.isEmpty() ? new tblWineries() : this.mapper.readValue(wineryJson, tblWineries.class);

		if(winery.getId()==null)
			winery.setName(wineryName);

		return winery;
	}

	/**
	 * 
	 * @param closure
	 * @return An object representing the row from tblClosures containing the desired closure
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 */
	public tblClosures getClosure(String closureName) throws JsonParseException, JsonMappingException, IOException
	{
		final String closureJson = this.requestsCreator.createGetRequest(apiUrl, "closures?action=getByName&name="+closureName);

		final tblClosures closure = closureJson.isEmpty() ? new tblClosures() : this.mapper.readValue(closureJson, tblClosures.class);

		if(closure.getId()==null)
			closure.setName(closureName);

		return closure;
	}

	/**
	 * 
	 * @param colour
	 * @return
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 */
	public tblColours getColour(String colourName) throws JsonParseException, JsonMappingException, IOException
	{
		final String colourJson = this.requestsCreator.createGetRequest(apiUrl, "colours?action=getByName&name="+colourName);

		final tblColours colour = colourJson.isEmpty() ? new tblColours() : this.mapper.readValue(colourJson, tblColours.class);

		if(colour.getId()==null)
			colour.setName(colourName);

		return colour;
	}

	/**
	 * Extract vintage and bottle size if it appears on the name and it wasn't found while parsing the website
	 * @param wine The wine we are building
	 */
	public tblWines completeDataFromName(tblWines wine, String merchantName) throws Exception
	{
		final CharMatcher digitMatcher = CharMatcher.digit();
		String wineName = wine.getName();

		//VINTAGE
		vintageLoop:
		for(int i=0;i<this.vintagePatterns.length;i++)
		{
			Matcher vintageMatcher = this.vintagePatterns[i].matcher(wineName);
			if(vintageMatcher.find())
			{
				final String vintageString = wineName.substring(vintageMatcher.start(), vintageMatcher.end());
				wineName = wineName.replace(vintageString, " ").replaceAll(this.vintagePatterns[0].pattern(), " ");
	
				if(wine.getVintage()==null)
				{
					Integer vintage = Integer.parseInt(digitMatcher.retainFrom(vintageString));
					switch(i)
					{
						case 1:
						{
							LocalDate date = LocalDate.now();
							vintage = date.getYear()-vintage;
							break vintageLoop;
						}
						case 2:
						{
							LocalDate date = LocalDate.now();
							if(date.getYear()%100<=vintage)
							{
								vintage += (date.getYear()/100) * 100;
							}
							else
							{
								vintage += ((date.getYear()/100)-1) * 100;
							}
							break vintageLoop;
						}
					}
					wine.setVintage(vintage);
				}
			}
		}

		//BOTTLE SIZE
		wineName = wineName.replaceAll(patternsOpening+"\\d+\\s*(x|\\*)"+patternsClosing, " ").trim();
		/*
		 * check the patterns one by one,
		 * if one of them matches we take the value and remove the bottlesize from the name
		 * */
		Float bottleSize = null;
		BottleSizeLoop:
		for(int i=0;i<this.bottleSizePatterns.length;i++)
		{
			//System.out.println(bottleSizePatterns[i].toString());
			Matcher bottleSizeMatcher = bottleSizePatterns[i].matcher(wineName);
			if(bottleSizeMatcher.find())
			{
				final String data = wineName.substring(bottleSizeMatcher.start(), bottleSizeMatcher.end());
				bottleSize = i!=3 ? Float.parseFloat(digitMatcher.or(CharMatcher.is('.')).retainFrom(data)) : 37.50f;
				wineName = wineName.replace(data, " ");

				switch(i)
				{
					case 1:
						bottleSize /= 10;
						break BottleSizeLoop;
					case 2:
						bottleSize *= 100;
						break BottleSizeLoop;
					default:
						break BottleSizeLoop;
				}
			}
		}
		//if we haven't set the size of the bottle yet and we have found it in the name, we add it
		if(wine.getBottleSize()==null && bottleSize!=null)
			wine.setBottleSize(bottleSize);


		//ABV
		final Matcher alcoholMatcher = this.alcoholVolumePattern.matcher(wineName);
		if(alcoholMatcher.find())
		{
			String data = wineName.substring(alcoholMatcher.start(), alcoholMatcher.end());
			wineName = wineName.replace(data, " ").trim();

			if(wine.getAbv()==null)
				wine.setAbv(Float.parseFloat(digitMatcher.or(CharMatcher.is('.')).retainFrom(data)));
		}

		//MERCHANT NAME
		if(wineName.contains(merchantName))
		{
			final Pattern merchantNamePattern = Pattern.compile(patternsOpening+Pattern.quote(merchantName)+patternsClosing);
			final Matcher merchantNameMatcher = merchantNamePattern.matcher(wineName);
			if(merchantNameMatcher.find())
			{
				final String data = wineName.substring(merchantNameMatcher.start(), merchantNameMatcher.end());
				wineName = wineName.replace(data, " ");
			}
			else
			{
				System.out.println("Merchant name found but not matched");
				wineName = wineName.replace(merchantName, "");
			}
		}

		wine.setName(wineName);
		return wine;
	}

	public String getImageName(String imageUrl	, Integer wineId)
	{
		String imageLink = this.escape(imageUrl.replaceFirst("https", "http").replaceAll(Pattern.quote(" "), "+"), "'");
		Integer fileFormatStart = imageLink.lastIndexOf(".")+1;
		Integer fileFormatEnd = imageLink.indexOf('?', fileFormatStart);
		if(fileFormatEnd<0)
			fileFormatEnd = imageLink.indexOf('&', fileFormatStart);

		String format;
		if(fileFormatEnd<0)
			format = imageLink.substring(fileFormatStart);
		else
			format = imageLink.substring(fileFormatStart, fileFormatEnd);

		String finalImageName = wineId+"."+format;

		return finalImageName;
	}
	/**
	 * 
	 * @param folder
	 * @param imageName
	 * @param downloadUrl
	 * @return
	 */
	public void getImage(String imageName, String downloadUrl)
	{
		try {
			try {
		    	this.ftp.connect(properties.getProperty("ftp.host.address"), Integer.parseInt(properties.getProperty("ftp.port")));
			} catch (IOException e) {
				System.out.println("Something went wrong while reaching the ftp server");
				e.printStackTrace();
				return;
			}
		    try {
		    	this.ftp.login(properties.getProperty("ftp.username"), properties.getProperty("ftp.password"));
			} catch (IOException e) {
				System.out.println("Something went wrong while loging in to the ftp server");
				e.printStackTrace();
				return;
			}

			this.ftp.enterLocalPassiveMode();
			this.ftp.setControlKeepAliveTimeout(300);
			this.ftp.setDefaultTimeout(300);
			this.ftp.setBufferSize((1024*1024)*10);

			try {
				this.ftp.setFileType(FTP.BINARY_FILE_TYPE);
			} catch (IOException e1) {
				System.out.println("An error occurred when setting ftp file transaction type to binary");
				e1.printStackTrace();
				return;
			}

			try {
				ftp.storeFile(properties.getProperty("ftp.folder")+"/"+imageName, new URL(downloadUrl).openStream());
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}

		} finally {
			if(ftp.isConnected())
			{
				try {
					ftp.logout();
					ftp.disconnect();
				} catch (IOException e) {
					System.out.println("Couldn't disconnect from the server");
					e.printStackTrace();
				}
			}
		}
	}

	public synchronized Integer insertWine(tblWines wine) throws NumberFormatException, JsonProcessingException, IOException
	{
		String response = requestsCreator.createPostRequest(this.apiUrl, "wines?action=addWine", this.mapper.writeValueAsString(wine));
		return Integer.valueOf(response);
	}

	public Boolean updateWine(tblWines wine) throws JsonProcessingException, IOException
	{
		String response = requestsCreator.createPostRequest(apiUrl, "wines?action=updateWine", new ObjectMapper().writeValueAsString(wine));
		return Boolean.valueOf(response);
	}

	private String escape(String text, String character)
	{
		if(text.contains(character))
			text = text.replace(character, "\\"+character);

		return text;
	}

	public tblWines mergeWines(tblWines wine, tblWines existingWine) {
		if(existingWine.getAbv()==null && wine.getAbv()!=null)
				existingWine.setAbv(wine.getAbv());

		if(existingWine.getAppellation()==null && wine.getAppellation()!=null)
			existingWine.setAppellation(wine.getAppellation());

		if(existingWine.getBottleSize()==null && wine.getBottleSize()!=null)
			existingWine.setBottleSize(wine.getBottleSize());

		if(existingWine.getClosure()==null && wine.getClosure()!=null)
			existingWine.setClosure(wine.getClosure());

		if(existingWine.getColour()==null && wine.getColour()!=null)
			existingWine.setColour(wine.getColour());

		if(existingWine.getCountry()==null && wine.getCountry()!=null)
			existingWine.setCountry(wine.getCountry());

		if(existingWine.getDefaultDescription().length() < wine.getDefaultDescription().length())
		{
			existingWine.setDefaultDescription(wine.getDefaultDescription());
			existingWine.setShortDescription(wine.getShortDescription());
		}

		if(existingWine.getGtin()==null && wine.getGtin()!=null)
			existingWine.setGtin(wine.getGtin());

		if(existingWine.getRegion()==null && wine.getRegion()!=null)
			existingWine.setRegion(wine.getRegion());

		if(existingWine.getVintage()==null && wine.getVintage()!=null)
			existingWine.setVintage(wine.getVintage());

		if(existingWine.getWinery()==null && wine.getWinery()!=null)
			existingWine.setWinery(wine.getWinery());

		return existingWine;
	}
}
