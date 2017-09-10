package winedunk.pf.services;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import javax.ejb.LocalBean;
import javax.ejb.Stateful;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.models.tblAppellations;
import winedunk.pf.models.tblClosures;
import winedunk.pf.models.tblColours;
import winedunk.pf.models.tblCountries;
import winedunk.pf.models.tblRegions;
import winedunk.pf.models.tblWineries;
import winedunk.pf.models.tblWines;

/**
 * Session Bean implementation class WineService
 */
@Stateful
@LocalBean
public class WineService {

	private final ExecutorService executor;
	private final RequestsCreator requestsCreator;
	private final ObjectMapper mapper;
	private String apiUrl;

	public WineService() 
	{
		this.executor = Executors.newFixedThreadPool(10);
		this.requestsCreator = new RequestsCreator();
		this.mapper = new ObjectMapper();	
	}

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
     */
    public tblWines getInstance(String apiUrl, String name, String gtin, String bottleSize, String vintage)
    {
    	this.setApiUrl(apiUrl);

    	tblWines wine;
    	//TODO test for possible NullPointerException
    	if(gtin!=null)
    	{
    		try {
    			wine = this.mapper .readValue(this.requestsCreator.createGetRequest(apiUrl, "wines?action=getByGtin&gtin="+gtin), tblWines.class);
    		} catch (IOException e) {
    			e.printStackTrace();
        		Thread.currentThread().interrupt();
        		return null;
    			//TODO handle
    		}
    	}
    	else
    	{
    		try {
    			String requestParameters = "action=getByNameBottleAndVintage"
		    					  + "&name="+name
		    					  + "&bottleSize="+bottleSize
		    					  + "&vintage="+vintage;
    			wine = this.mapper .readValue(this.requestsCreator.createGetRequest(apiUrl, "wines?"+requestParameters), tblWines.class);
    		} catch (IOException e) {
    			// TODO handle
    			e.printStackTrace();
        		Thread.currentThread().interrupt();
        		return null;
    		}
    	}
    	return wine;
    }
    /**
     * 
     * @param country
     * @return
     */
    public tblCountries getCountry(String country)
    {
    	switch(country.toLowerCase())
		{
			case "california":
				country="California";
			case "usa":
			case "u.s.a.":
			case "america":
			case "united states of america":
				country = "United States";
				break;
			case "england":
			case "scotland":
			case "ireland":
			case "wales":
			case "uk":
			case "great britain":
				country = "United Kingdom";
				break;
			case "republic of macedonia":
				country="Macedonia";
		}

		if(country.contains("Product of"));
		country = country.replace("Product of ", "");
    	
		//String countryJson = new RequestsCreator()
		return null;
    }

    /**
     * 
     * @param region
     * @return
     */
    public tblRegions getRegion(String region)
    {
    	return null;
    }
    public tblRegions getRegion(Integer regionId)
    {
    	return null;
    }

    /**
     * 
     * @param appellation
     * @return
     */
    public tblAppellations getAppellation(String appellation)
    {
    	return null;
    }
    public tblAppellations getAppellation(Integer appellationId)
    {
    	return null;
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

    /**
     * 
     * @param imageUrl
     * @return
     * @throws IOException 
     */
    public String getImage(FTPClient ftp, String folder, String imageUrl, Integer wineId) throws IOException
    {
    	String imageLink = this.escape(imageUrl.replaceFirst("https", "http").replaceAll(Pattern.quote(" "), "%20"), "'");
		String format = imageLink.substring(imageLink.lastIndexOf(".")+1);
		String finalImageName = wineId+"."+format;

		ftp.enterLocalPassiveMode();
		ftp.setFileType(FTP.BINARY_FILE_TYPE);

		this.executor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					ftp.storeFile(folder+"/"+finalImageName, new URL(imageLink).openStream());
				} catch (IOException e) {
					//TODO handle
					e.printStackTrace();
					return;
				}
			}
		});

		return finalImageName;
    }
    
    private String escape(String text, String character)
	{
		if(text.contains(character))
			text = text.replace(character, "\\"+character);

		return text;
	}

    public Integer insertWine(tblWines wine) throws NumberFormatException, JsonProcessingException, IOException
    {
    	String response = requestsCreator.createPostRequest(this.apiUrl, "wines?action=addWine", new ObjectMapper().writeValueAsString(wine));
    	return Integer.valueOf(response);
		
    }

    public void updateWine(tblWines wine)
    {
    	
    }
}
