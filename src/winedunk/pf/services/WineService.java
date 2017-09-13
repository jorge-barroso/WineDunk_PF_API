package winedunk.pf.services;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ejb.LocalBean;
import javax.ejb.Stateful;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Stateful
@LocalBean
public class WineService {
	private String apiUrl;
	private FTPClient ftp;
	private final RequestsCreator requestsCreator = new RequestsCreator();
	private final ObjectMapper mapper = new ObjectMapper();
	private final Map<String, String> countries = new HashMap<String, String>();
	private final Pattern[] bottleSizePatterns = new Pattern[] {Pattern.compile("\\d+cl"), Pattern.compile("\\d+ml"), Pattern.compile("\\d+L"), Pattern.compile("\\d+Ltr")};
	private final Pattern vintagePattern = Pattern.compile("[0-9]{4}");

	/**
	 * @throws IOException 
	 * 
	 */
	public WineService(FTPClient ftp) throws IOException 
	{
		for(String countryCode : Locale.getISOCountries())
		{
        	this.countries.put(new Locale("", countryCode).getDisplayCountry(), countryCode);
		}	
	}

	/**
	 * 
	 * @param apiUrl
	 * @throws IOException 
	 */
	public void initialise(FTPClient ftp, String apiUrl) throws IOException
	{
		this.ftp = ftp;
		this.ftp.enterLocalPassiveMode();
		this.ftp.setFileType(FTP.BINARY_FILE_TYPE);

		this.setApiUrl(apiUrl);
	}

	/**
	 * 
	 * @param apiUrl
	 */
	public void setApiUrl(String apiUrl)
	{
		this.apiUrl = apiUrl;
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
									 + "&name="+name
									 + "&bottleSize="+bottleSize
									 + "&vintage="+vintage;
			wine = this.mapper .readValue(this.requestsCreator.createGetRequest(this.apiUrl, "wines?"+requestParameters), tblWines.class);
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

		if(countryName.contains("Product of"));
		countryName = countryName.replace("Product of ", "");

		TblPFCountryNameMappingTable countryNameMapping;
		try {
			String countryNameMappingJson = this.requestsCreator.createGetRequest(apiUrl, "TblPFCountryNameMappingTableController?action=getByName&name="+countryName);
			countryNameMapping = this.mapper.readValue(countryNameMappingJson, TblPFCountryNameMappingTable.class);
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

		if(countryNameMapping.getId()!=null)
			countryName = countryNameMapping.getTblCountries().getName();

		tblCountries country;
		try {
			String countryJson = this.requestsCreator.createGetRequest(this.apiUrl, "countries?action=getByName&name="+countryName);
			country = this.mapper.readValue(countryJson, tblCountries.class);
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
			CountryCode cc = countries.get(countryName)==null ? null : CountryCode.getByCode(countries.get(countryName));

			country = new tblCountries();
			if(cc==null)
			{
				if(countryName.equals(NoDataFieldsValues.NO_COUNTRY.toString() ))
				{
					country.setName(countryName)
					   .setIsoAlpha2Code("nc")
					   .setIsoAlpha3Code("nc")
					   .setIsoNumericCode(0);
				}
				else
				{
					//TODO notify of wrong naming for a country
				}
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
		String regionJson = this.requestsCreator.createGetRequest(this.apiUrl, "regions?action=getByName&name="+regionName);
		
		return this.mapper.readValue(regionJson, tblRegions.class);
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
		String regionJson = this.requestsCreator.createGetRequest(this.apiUrl, "regions?action=getRegion&id="+regionId);
		
		return this.mapper.readValue(regionJson, tblRegions.class);
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
		return this.getAppellation("getByName", "name="+appellationName);
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
		String appellationJson = this.requestsCreator.createGetRequest(apiUrl, "appellations?"+parameters);

		return this.mapper.readValue(appellationJson, tblAppellations.class);
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
		String wineryJson = this.requestsCreator.createGetRequest(apiUrl, "wineries?action=getByName&name="+wineryName);

		return this.mapper.readValue(wineryJson, tblWineries.class);
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
		String closureJson = this.requestsCreator.createGetRequest(apiUrl, "closures?action=getByName&name="+closureName);

		return this.mapper.readValue(closureJson, tblClosures.class);
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
		String colourJson = this.requestsCreator.createGetRequest(apiUrl, "colours?action=getByName&name="+colourName);

		return this.mapper.readValue(colourJson, tblColours.class);
	}

	/**
	 * Extract vintage and bottle size if it appears on the name and it wasn't found while parsing the website
	 * @param wine The wine we are building
	 */
	public tblWines completeDataFromName(tblWines wine)
	{
		//VINTAGE
		if(wine.getVintage()==null)
		{
			final Matcher vintageMatcher = this.vintagePattern.matcher(wine.getName());

			if(vintageMatcher.find())
			{
				String vintage = wine.getName().substring(vintageMatcher.start(), vintageMatcher.end());
				wine.setName(wine.getName().replace(vintage, ""));
	
				if(wine.getVintage()==null)
				{
					wine.setVintage(Integer.parseInt(vintage));
				}
			}
		}

		if(wine.getBottleSize()==null)
		{
			/*
			 * check the patterns one by one,
			 * if one of them matches we take the value and remove the bottlesize from the name
			 * */
			Float bottleSize = null;
			BottleSizeLoop:
			for(int i=0;i<this.bottleSizePatterns.length;i++)
			{
				Matcher bottleSizeMatcher = bottleSizePatterns[i].matcher(wine.getName());
				if(bottleSizeMatcher.find())
				{
					wine.setName(wine.getName().replace(wine.getName().substring(bottleSizeMatcher.start(), bottleSizeMatcher.end()), "").trim());

					switch(i)
					{
						case 0:
							bottleSize = Float.parseFloat(wine.getName().substring(bottleSizeMatcher.start(), bottleSizeMatcher.end()).replace("cl", ""));
							break BottleSizeLoop;
						case 1:
							bottleSize = Float.parseFloat(wine.getName().substring(bottleSizeMatcher.start(), bottleSizeMatcher.end()).replace("ml", ""))/10;
							break BottleSizeLoop;
						case 2:
							bottleSize = Float.parseFloat(wine.getName().substring(bottleSizeMatcher.start(), bottleSizeMatcher.end()).replace("L", ""))*100;
							break BottleSizeLoop;
						case 3:
							bottleSize = Float.parseFloat(wine.getName().substring(bottleSizeMatcher.start(), bottleSizeMatcher.end()).replace("Ltr", ""))*100;
							break BottleSizeLoop;
					}
				}
			}
			
			//if we haven't set the size of the bottle yet and we have found it in the name, we add it
			if(wine.getBottleSize()==null && bottleSize!=null)
			{
				wine.setBottleSize(bottleSize);
			}
		}

		return wine;
	}

	public String getImageName(String imageUrl, Integer wineId)
	{
		String imageLink = this.escape(imageUrl.replaceFirst("https", "http").replaceAll(Pattern.quote(" "), "%20"), "'");
		String format = imageLink.substring(imageLink.lastIndexOf(".")+1);
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
	public void getImage(String folder, String imageName, String downloadUrl)
	{
		try {
			ftp.storeFile(folder+"/"+imageName, new URL(downloadUrl).openStream());
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}

	public Integer insertWine(tblWines wine) throws NumberFormatException, JsonProcessingException, IOException
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
}
