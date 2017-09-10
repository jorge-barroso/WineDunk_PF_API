package winedunk.pf.services;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
	private final RequestsCreator requestsCreator;
	private final ObjectMapper mapper;
	private String apiUrl;
	private final Map<String, String> countries = new HashMap<String, String>();
	FTPClient ftp;
	/**
	 * @throws IOException 
	 * 
	 */
	public WineService(FTPClient ftp) throws IOException 
	{
		this.requestsCreator = new RequestsCreator();
		this.mapper = new ObjectMapper();

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
		//TODO test for possible NullPointerException
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
		switch(countryName.toLowerCase())
		{
			case "usa":
			case "u.s.a.":
			case "america":
			case "united states of america":
				countryName = "United States";
				break;
			case "england":
			case "scotland":
			case "ireland":
			case "wales":
			case "uk":
			case "great britain":
				countryName = "United Kingdom";
				break;
			case "republic of macedonia":
				countryName="Macedonia";
		}

		if(countryName.contains("Product of"));
		countryName = countryName.replace("Product of ", "");

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
	public tblAppellations getAppellation(Integer appellationId) throws JsonParseException, JsonMappingException, IOException
	{
		return this.getAppellation("getAppellation", "id="+appellationId);
	}
	private tblAppellations getAppellation(String action, String parameters) throws JsonParseException, JsonMappingException, IOException
	{
		String appellationJson = this.requestsCreator.createGetRequest(apiUrl, "appellations?"+parameters);

		return this.mapper.readValue(appellationJson, tblAppellations.class);
	}

	/**
	 * 
	 * @param winery
	 * @return
	 */
	public tblWineries getWinery(String winery)
	{
		return null;
	}

	/**
	 * 
	 * @param closure
	 * @return
	 */
	public tblClosures getClosure(String closure)
	{
		return null;
	}

	/**
	 * 
	 * @param colour
	 * @return
	 */
	public tblColours getColour(String colour)
	{
		return null;
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
