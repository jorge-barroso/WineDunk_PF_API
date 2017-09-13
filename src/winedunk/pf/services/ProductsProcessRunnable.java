package winedunk.pf.services;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import javax.ejb.EJB;

import org.apache.commons.net.ftp.FTPClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.helpers.NoDataFieldsValues;
import winedunk.pf.helpers.TblWineFields;
import winedunk.pf.models.TblWinesWineType;
import winedunk.pf.models.Tblpfmerchanthtmlparsing;
import winedunk.pf.models.Tblpfparsingextractionmethod;
import winedunk.pf.models.Tblpfproduct;
import winedunk.pf.models.tblAppellations;
import winedunk.pf.models.tblPartnersProducts;
import winedunk.pf.models.tblRegions;
import winedunk.pf.models.tblShops;
import winedunk.pf.models.tblWineTypes;
import winedunk.pf.models.tblWineries;
import winedunk.pf.models.tblWines;

public class ProductsProcessRunnable implements Callable<Integer> {

	private final RequestsCreator requestsCreator = new RequestsCreator();
	private final ObjectMapper mapper = new ObjectMapper();
	private final Tblpfproduct product;
	private final Properties properties;
	private final Date executionDate;

	@EJB
	private PartnersProductsService partnerProductsService;
	@EJB
	private WineService wineService;

	/**
	 * 
	 * @param product
	 * @param properties
	 * @param executionDate
	 * @param ftp
	 */
	public ProductsProcessRunnable(Tblpfproduct product, Properties properties, Date executionDate, FTPClient ftp)
	{
		this.product = product;
		this.properties = properties;
		this.executionDate = executionDate;

		try {
			this.wineService.initialise(ftp, properties.getProperty("crud.url"));
		} catch (IOException e) {
			System.out.println("Error setting ftp transfer type to binary");
			e.printStackTrace();
			return;
		}
	}

	/**
	 * <p>Default call method that will run in background automatically and return the product feed id of this product, that will be stored in a list on the main asynchronous thread on @see winedunk.pf.controllers.ProductsProcessor#doGet()</p>
	 * <p>We will use that list later to count how many wines have been processed </p>
	 * 
	 */
	@Override
	public Integer call() throws Exception {
		productProcess(this.product);

		if(!Thread.currentThread().isInterrupted())
			return this.product.getTblpf().getId();
		else
			return null;
	}

	/**
     * 
     * @param product
     */
    private void productProcess(Tblpfproduct product)
    {
    	//Get wine values by parsing the website
    	Map<String, String> wineValues;
		try {
			wineValues = this.parseWebsite(Jsoup.parse(this.requestsCreator.createGetRequest(product.getProductURL())),
										   this.getParsingInstructions(product.getMerchantName()));
		} catch (JsonParseException e4) {
			System.out.println("While trying to get the merchant by name from the CRUD, response doesn't seem to have a valid JSON format");
			e4.printStackTrace();
			return;
		} catch (JsonMappingException e4) {
			System.out.println("While trying to get the merchant by name from the CRUD, response doesn't look like a serialised merchant");
			e4.printStackTrace();
			return;
		} catch (IOException e4) {
			System.out.println("Exception occurred while reaching CRUD");
			e4.printStackTrace();
			return;
		}

		if(Thread.currentThread().isInterrupted())
		{
			System.out.println("Thread has been interrupted");
			return;
		}

		//get possibly existing wine and product
    	tblPartnersProducts partnerProduct;
		try {
			partnerProduct = this.partnerProductsService.getInstance(properties.getProperty("crud.url"), product.getPartnerProductId(), product.getMerchantProductId());
		} catch (IOException e1) {
			System.out.println("While trying to find a possible copy of the product already existing in the db (and retrieve it for edition) the CRUD wasn't reachable or there was a low-level I/O exception, please check the server");
			e1.printStackTrace();
			return;
		}
		
		//if we have the wine in the product, we get it, otherwise, we instanciate a new one by different methods (looking for possibly already existing wine)
		tblWines wine;
		try {
			wine = partnerProduct.getTblWines() != null ? partnerProduct.getTblWines() : this.wineService.getInstance(product.getName(),
																						 							  wineValues.get(TblWineFields.GTIN.toString()),
																						 							  wineValues.get(TblWineFields.BOTTLE_SIZE.toString()),
																						 							  wineValues.get(TblWineFields.VINTAGE.toString()));
		} catch (JsonParseException e4) {
			System.out.println("While trying to find the possibly existing wine, the JSON response from the CRUD doesn't seem to have a valid format");
			e4.printStackTrace();
			return;
		} catch (JsonMappingException e4) {
			System.out.println("While trying to find the possibly existing wine, the JSON response from the CRUD doesn't seem to have a valid format");
			e4.printStackTrace();
			return;
		} catch (IOException e4) {
			System.out.println("While trying to find the possibly existing wine, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
			e4.printStackTrace();
			return;
		}

		//set wine values
    	wine = this.setWineValues(wine, wineValues, product.getName(), product.getPartnerProductDescription(), product.getProductType());

    	if(Thread.currentThread().isInterrupted())
    		return;

		//if the wine didn't exist previously, we will insert it to retrieve its new id and then get the image
		if(wine.getId()==null)
		{
			try {
				wine.setId(this.wineService.insertWine(wine));
			} catch (NumberFormatException e) {
				System.out.println("Id returned by the CRUD while inserting the wine was not a proper number");
				e.printStackTrace();
				return;
			} catch (JsonProcessingException e) {
				System.out.println("An exception came up while serialising the wine to JSON before sending it for insertion in the database");
				e.printStackTrace();
				return;
			} catch (IOException e) {
				System.out.println("While sending the wine to the CRUD for insertion, the CRUD was not reachable");
				e.printStackTrace();
				return;
			}
		}

		//Sanitise the name removing unwanted details and extract those details as possible values  for other fields in the tblWines table
		wine = this.wineService.completeDataFromName(wine);

		if(wine.getImageURL()==null /*TODO || blank "no-image" image and this product contains a valid image*/)
		{
			String finalImageName = this.wineService.getImageName(product.getImageURL(), wine.getId());
			wineService.getImage(properties.getProperty("image.host.folder.main"), finalImageName, product.getImageURL());			

			wine.setImageURL(properties.getProperty("image.host.images.path")+"/"+finalImageName);
		}

		//Work out the wine type
		this.setWinesWineType(wine, wineValues.get(TblWineFields.WINE_TYPE.toString()));

		if(Thread.currentThread().isInterrupted())
			return;

		//update wine
		try {
			this.wineService.updateWine(wine);
		} catch (JsonProcessingException e2) {
			System.out.println("An exception came up while serialising the wine to JSON before sending it for update in the database");
			e2.printStackTrace();
			return;
		} catch (IOException e2) {
			System.out.println("While sending the wine to the CRUD for update, the CRUD was not reachable");
			e2.printStackTrace();
			return;
		}

		//set current product values
		partnerProduct = this.setPartnerProductsValues(partnerProduct, product, wine);

		//if we have an id it was already in the db so we update it, otherwise, we insert it
		if(partnerProduct.getId()==null)
		{
			try {
				partnerProduct.setId(this.partnerProductsService.insertProduct(partnerProduct));
			} catch (NumberFormatException e) {
				System.out.println("Id returned by the CRUD while inserting the product was not a proper number");
				e.printStackTrace();
				return;
			} catch (JsonProcessingException e) {
				System.out.println("An exception came up while serialising the product to JSON before sending it for insertion in the database");
				e.printStackTrace();
				return;
			} catch (IOException e) {
				System.out.println("While sending the product to the CRUD for insertion, the CRUD was not reachable");
				e.printStackTrace();
				return;
			}
		}
		else
		{
			try {
				this.partnerProductsService.updateProduct(partnerProduct);
			} catch (JsonProcessingException e2) {
				System.out.println("An exception came up while serialising the product to JSON before sending it for update in the database");
				e2.printStackTrace();
				return;
			} catch (IOException e2) {
				System.out.println("While sending the product to the CRUD for update, the CRUD was not reachable");
				e2.printStackTrace();
				return;
			}
		}
    }

    /**
     * Here we get the parsing instructions for each value and the html
     * 
     * @param merchantName
     * @return
     * @throws InterruptedException
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonParseException 
     */
    private List<Tblpfmerchanthtmlparsing> getParsingInstructions(String merchantName)
    {
		tblShops merchant = this.getMerchant(merchantName);

		if(Thread.currentThread().isInterrupted())
			return null;

		if(merchant==null)
		{
			System.out.println("Couldn't find merchant, so no parsing data can be provided");
			Thread.currentThread().interrupt();
			return null;
		}

		//get parsing instructions by the merchant
		String merchantParsingString;
		try {
			merchantParsingString = this.requestsCreator.createPostRequest(this.properties.getProperty("crud.url"), "TblPfMerchantsHTMLParsing" , "action=getByMerchant&id="+merchant.getId());
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Couldn't reach CRUD destination while attempting to get the list of parsing instructions");
			Thread.currentThread().interrupt();
			return null;
		}
		
		try {
			return this.mapper.readValue(merchantParsingString, new TypeReference<List<Tblpfmerchanthtmlparsing>>(){});
		} catch (JsonParseException e) {
			System.out.println("While trying to get the merchant parsing instructions, the JSON response by the CRUD doesn't seem to have a valid format");
			e.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		} catch (JsonMappingException e) {
			System.out.println("While trying to get the merchant parsing instructions, the JSON response by the CRUD couldn't be mapped with a valid Tblpfmerchanthtmlparsing object");
			e.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		} catch (IOException e) {
			System.out.println("While trying to get the merchant parsing instructions, a low level I/O exception occurred");
			e.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		}
    }

    /**
     * 
     * @param htmlDoc
     * @param merchantParsings
     * @return
     */
    private Map<String, String> parseWebsite(Document htmlDoc, List<Tblpfmerchanthtmlparsing> merchantParsings)
    {
    	Map<String, String> wineValues = new HashMap<String, String>();
    	
    	//Extract all elements from html
    	Elements elements = htmlDoc.getAllElements();
    	//for each element, look for keywords to find the remaining values of the wine
    	for(int i=0;i<elements.size();i++)
    	{
    		for(Tblpfmerchanthtmlparsing merchantParsing : merchantParsings)
        	{
    			//if we have it already inserted on the values' map and it's not an empty value, we go for the next key
    			if(wineValues.containsKey(merchantParsing.getTblpfextractioncolumn().getColumnName()) && !wineValues.get(merchantParsing.getTblpfextractioncolumn().getColumnName()).trim().isEmpty())
    				continue;
    			//if we don't find te current keyword go for the next one
        		if(!elements.get(i).ownText().equals(merchantParsing.getNameInWeb()))
        			continue;

        		Tblpfparsingextractionmethod extractionMethod = merchantParsing.getTblpfparsingextractionmethod();
    			String extractedValue = null;
    			//extract value
    			switch(extractionMethod.getMethod())
    			{
    				case "SameTag":
    					extractedValue = elements.get(i).ownText().replace(merchantParsing.getNameInWeb(), "").trim();
    					break;
    				case "Children":
    					extractedValue = elements.get(i).child(0).ownText().trim();
    					break;
    				case "NextTag":
    					extractedValue = elements.get(++i).ownText().trim();
    					break;
    			}

    			//Store values
    			wineValues.put(merchantParsing.getTblpfextractioncolumn().getColumnName(), extractedValue);
        	}
    	}
    	return wineValues;
    }

    /**
     * 
     * @param wine
     * @param wineValues
     * @param name
     * @param description
     * @param wineType
     * @return An updated copy of the wine with the new values
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonParseException 
     */
    private tblWines setWineValues(tblWines wine, Map<String, String> wineValues, String name, String description, String wineType)
    {
    	//Set plain values that will not be related to another table, and thus can be just null in case we don't have them
		wine.setName(name);
		/*
		 * Just checking which one is bigger would be easier but this way we avoid possible null pointer exceptions
		 * As the AND condition stops checking any more conditions once one is false, if it's null it won't check the length and we will avoid the exception 
		 */
		if((wine.getDefaultDescription()!=null && description.length()>wine.getDefaultDescription().length()) || wine.getDefaultDescription()==null)
		{
			wine.setDefaultDescription(description);
			wine.setShortDescription(description.substring(0, 161)+"...");
		}	
		wine.setDeleted(false);
		wine.setAbv(Float.valueOf(wineValues.get(TblWineFields.ABV.toString())));
		wine.setBottleSize(Float.valueOf(wineValues.get(TblWineFields.BOTTLE_SIZE.toString())));
		wine.setGtin(wineValues.get(TblWineFields.GTIN.toString()));

		//Get Closure
		try {
			if(wineValues.containsKey(TblWineFields.CLOSURE.toString()))
					wine.setClosure(this.wineService.getClosure(wineValues.get(TblWineFields.CLOSURE.toString())));
			else
				wine.setClosure(this.wineService.getClosure(NoDataFieldsValues.NO_CLOSURE.toString()));
		} catch (JsonParseException e1) {
			System.out.println("While trying to get the closure by its name from the CRUD, the response doesn't seem to have a valid JSON format");
			e1.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		} catch (JsonMappingException e1) {
			System.out.println("While trying to get the closure by its name from the CRUD, the JSON response couldn't be mapped to a tblClosure object (discrepancies between model and JSON)");
			e1.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		} catch (IOException e1) {
			System.out.println("While trying to get the closure by its name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
			e1.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		}

		//Get Colour
		try {
			if(wineValues.containsKey(TblWineFields.COLOUR.toString()))
				wine.setColour(this.wineService.getColour(wineValues.get(TblWineFields.COLOUR.toString())));
			else
				wine.setColour(this.wineService.getColour(NoDataFieldsValues.NO_COLOUR.toString()));
		} catch (JsonParseException e1) {
			System.out.println("While trying to get the colour by its name from the CRUD, the response doesn't seem to have a valid JSON format");
			e1.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		} catch (JsonMappingException e1) {
			System.out.println("While trying to get the colour by its name from the CRUD, the JSON response couldn't be mapped to a tblClosure object (discrepancies between model and JSON)");
			e1.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		} catch (IOException e1) {
			System.out.println("While trying to get the colour by its name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
			e1.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		}

		//Get Winery
		tblWineries winery;
		try {
			if(wineValues.containsKey(TblWineFields.WINERY.toString()))
				winery = this.wineService.getWinery(wineValues.get(TblWineFields.WINERY.toString()));
			else
				winery = this.wineService.getWinery(NoDataFieldsValues.NO_WINERY.toString());
		} catch (JsonParseException e1) {
			System.out.println("While trying to get the winery by its name from the CRUD, the response doesn't seem to have a valid JSON format");
			e1.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		} catch (JsonMappingException e1) {
			System.out.println("While trying to get the winery by its name from the CRUD, the JSON response couldn't be mapped to a tblClosure object (discrepancies between model and JSON)");
			e1.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		} catch (IOException e1) {
			System.out.println("While trying to get the winery by its name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
			e1.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		}

		//Get Appellation
		tblAppellations appellation;
		if(wineValues.containsKey(TblWineFields.APPELLATION.toString()))
		{
			try {
				appellation = this.wineService.getAppellation(TblWineFields.APPELLATION.toString());
			} catch (JsonParseException e) {
				System.out.println("While trying to get the appellation by its name, the JSON response from the CRUD doesn't seem to have a valid format");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (JsonMappingException e) {
				System.out.println("While trying to get the appellation by its name, the JSON response from the CRUD doesn't seem to have a valid format");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (IOException e) {
				System.out.println("While trying to get the appellation by its name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			}
		}
		else if(wine.getWinery()!=null)
		{
			try {
				appellation = this.wineService.getAppellation(wine.getWinery().getAppellationId());
			} catch (JsonParseException e) {
				System.out.println("While trying to get the appellation by its id taken from the winery, the JSON response from the CRUD doesn't seem to have a valid format");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (JsonMappingException e) {
				System.out.println("While trying to get the appellation by its id taken from the winery, the JSON response from the CRUD doesn't seem to have a valid format");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (IOException e) {
				System.out.println("While trying to get the appellation by its id taken from the winery, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			}
		}
		else
		{
			try {
				appellation = this.wineService.getAppellation(NoDataFieldsValues.NO_APPELLATION.toString());
			} catch (JsonParseException e) {
				System.out.println("While trying to get the default 'No Appellation' appellation, the JSON response from the CRUD doesn't seem to have a valid format");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (JsonMappingException e) {
				System.out.println("While trying to get the default 'No Appellation' appellation, the JSON response from the CRUD doesn't seem to have a valid format");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (IOException e) {
				System.out.println("While trying to get the default 'No appellation' appellation, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			}
		}

		//Get Region
		tblRegions region;
		if(wineValues.containsKey(TblWineFields.REGION.toString()))
		{
			//Get region by name with the value extracted from the website
			try {
				region = this.wineService.getRegion(wineValues.get(TblWineFields.REGION.toString()));
			} catch (JsonParseException e) {
				System.out.println("While trying to get the region by its name, the JSON response from the CRUD doesn't seem to have a valid format");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (JsonMappingException e) {
				System.out.println("While trying to get the region by its name, the JSON response from the CRUD doesn't seem to have a valid format");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (IOException e) {
				System.out.println("While trying to get the region by its name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			}
		}
		else if(wine.getAppellation()!=null)
		{
			//Get it from the appellation
			region = wine.getAppellation().getTblRegions();
		}
		else if(wine.getWinery()!=null)
		{
			//Get the region by the regionId field in the winery
			try {
				region = this.wineService.getRegion(wine.getWinery().getRegionId());
			} catch (JsonParseException e) {
				System.out.println("While trying to get the region by its id taken from the winery, the JSON response from the CRUD doesn't seem to have a valid format");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (JsonMappingException e) {
				System.out.println("While trying to get the region by its id taken from the winery, the JSON response from the CRUD doesn't seem to have a valid format");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (IOException e) {
				System.out.println("While trying to get the region by its id taken from the winery, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			}
		}
		else
		{
			//Get the default "No region" field
			try {
				region = this.wineService.getRegion(NoDataFieldsValues.NO_REGION.toString());
			} catch (JsonParseException e) {
				System.out.println("While trying to get the default 'No Region' region, the JSON response from the CRUD doesn't seem to have a valid format");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (JsonMappingException e) {
				System.out.println("While trying to get the default 'No Region' region, the JSON response from the CRUD doesn't seem to have a valid format");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (IOException e) {
				System.out.println("While trying to get the default 'No Region' region, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
				e.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			}
		}

		//Get Country
		if(wineValues.containsKey(TblWineFields.COUNTRY.toString()))
			wine.setCountry(this.wineService.getCountry(wineValues.get(TblWineFields.COUNTRY.toString())));
		else if(wine.getRegion()!=null)
			wine.setCountry(wine.getRegion().getTblCountries());
		else if(wine.getAppellation()!=null)
			wine.setCountry(wine.getAppellation().getTblCountries());
		else if(wine.getWinery()!=null)
			wine.setCountry(wine.getWinery().getTblCountries());
		else
			wine.setCountry(this.wineService.getCountry(NoDataFieldsValues.NO_COUNTRY.toString()));


		//Add country to the region if necessary and then insert or update it 
		if(region.getTblCountries()==null)
		{
			region.setTblCountries(wine.getCountry());
			if(region.getId()==null)
			{
				String id;
				try {
					id = this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "regions?action=addRegion", this.mapper.writeValueAsString(region));
				} catch (JsonProcessingException e) {
					e.printStackTrace();
					System.out.println("While sending a request to the CRUD to insert the region, something went wrong serialising it to JSON");
					Thread.currentThread().interrupt();
					return null;
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Couldn't reach the crud while sending a request to insert the region");
					Thread.currentThread().interrupt();
					return null;
				}
				
				region.setId(Integer.valueOf(id));
			}
			else
			{
				try {
					this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "regions?action=updateRegion", this.mapper.writeValueAsString(region));
				} catch (JsonProcessingException e) {
					e.printStackTrace();
					System.out.println("While sending a request to the CRUD to update the region, something went wrong serialising it to JSON");
					Thread.currentThread().interrupt();
					return null;
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Couldn't reach the crud while sending a request to update the region");
					Thread.currentThread().interrupt();
					return null;
				}
			}
		}
		wine.setRegion(region);

		//Add country and region to the appellation if necessary and insert or update it
		if(appellation.getTblCountries()==null || appellation.getTblRegions()==null)
		{
			if(appellation.getTblCountries()==null)
				appellation.setTblCountries(wine.getCountry());
			if(appellation.getTblRegions()==null)
				appellation.setTblRegions(wine.getRegion());
			
			if(appellation.getId()==null)
			{
				String id;
				try {
					id = this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "appellations?action=addAppellation", this.mapper.writeValueAsString(appellation));
				} catch (JsonProcessingException e) {
					e.printStackTrace();
					System.out.println("While sending a request to the CRUD to insert the appellation, something went wrong serialising it to JSON");
					Thread.currentThread().interrupt();
					return null;
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Couldn't reach the crud while sending a request to insert the appellation");
					Thread.currentThread().interrupt();
					return null;
				}
				
				appellation.setId(Integer.valueOf(id));
			}
			else
			{
				try {
					this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "appellations?action=updateAppellation", this.mapper.writeValueAsString(appellation));
				} catch (JsonProcessingException e) {
					e.printStackTrace();
					System.out.println("While sending a request to the CRUD to update the appellation, something went wrong serialising it to JSON");
					Thread.currentThread().interrupt();
					return null;
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Couldn't reach the crud while sending a request to update the appellation");
					Thread.currentThread().interrupt();
					return null;
				}
			}
		}

		wine.setAppellation(appellation);

		//Add country, region and appellation to the winery if needed and insert or update it
		if(winery.getAppellationId()==null || winery.getRegionId()==null || winery.getTblCountries()==null)
		{
			if(winery.getAppellationId()==null)
				winery.setAppellationId(wine.getAppellation().getId());
			if(winery.getRegionId()==null)
				winery.setRegionId(wine.getRegion().getId());
			if(winery.getTblCountries()==null)
				winery.setTblCountries(wine.getCountry());

			if(winery.getId()==null)
			{
				String id;
				try {
					id = this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "wineries?action=addWinery", this.mapper.writeValueAsString(winery));
				} catch (JsonProcessingException e) {
					e.printStackTrace();
					System.out.println("While sending a request to the CRUD to insert the winery, something went wrong serialising it to JSON");
					Thread.currentThread().interrupt();
					return null;
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Couldn't reach the crud while sending a request to insert the winery");
					Thread.currentThread().interrupt();
					return null;
				}
				
				winery.setId(Integer.valueOf(id));
			}
			else
			{
				try {
					this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "wineries?action=updateWinery", this.mapper.writeValueAsString(winery));
				} catch (JsonProcessingException e) {
					e.printStackTrace();
					System.out.println("While sending a request to the CRUD to update the winery, something went wrong serialising it to JSON");
					Thread.currentThread().interrupt();
					return null;
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Couldn't reach the crud while sending a request to update the winery");
					Thread.currentThread().interrupt();
					return null;
				}
			}
		}
		
		wine.setWinery(winery);

		return wine;
    }

    /**
     * 
     * @param partnersProducts
     * @param productStandard
     * @return
     * @throws JsonParseException
     * @throws JsonMappingException
     * @throws IOException
     */
    private tblPartnersProducts setPartnerProductsValues(tblPartnersProducts partnersProducts, Tblpfproduct productStandard, tblWines wine)
    {
    	partnersProducts.setDeleted(false)
    					.setDeletedDate(null)
    					.setLastUpdated(this.executionDate)
    					.setPartnerDestinationUrl(productStandard.getClicktag())
    					.setPartnerId(productStandard.getTblpf().getTblPartners())
    					.setPartnerMerchantDeliveringCost(productStandard.getDeliveryCost())
    					//.setPartnerMerchantId(productStandard.getPartnerMerchantId())
    					.setPartnerMerchantProductId(productStandard.getMerchantProductId())
    					.setPartnerMerchantStock(null)
    					.setPartnerProductId(productStandard.getPartnerProductId())
    					.setPartnerProductPrice(productStandard.getPrice())
    					.setShopId(this.getMerchant(productStandard.getMerchantName()))
    					.setTblWines(wine);
    	return partnersProducts;
    }

    /**
     * 
     * @param wine
     * @param wineTypeName
     * @throws JsonParseException
     * @throws JsonMappingException
     * @throws IOException
     */
    public void setWinesWineType(tblWines wine, String wineTypeName)
	{
		//take wine type
		tblWineTypes wineType;
		try {
			String wineTypeJson = this.requestsCreator.createGetRequest(this.properties.getProperty("crud.url"), "winetypes?action=getByName&name="+wineTypeName);
			wineType = this.mapper.readValue(wineTypeJson, tblWineTypes.class);
		} catch (JsonParseException e1) {
			System.out.println("While trying to get the possibly existing wine type by its name from the CRUD, the response doesn't seem to have a valid JSON format");
			e1.printStackTrace();
			Thread.currentThread().interrupt();
			return;
		} catch (JsonMappingException e1) {
			System.out.println("While trying to get the possibly existing wine type by its name from the CRUD, the JSON response couldn't be mapped to a tblClosure object (discrepancies between model and JSON)");
			e1.printStackTrace();
			Thread.currentThread().interrupt();
			return;
		} catch (IOException e1) {
			System.out.println("While trying to get the possibly existing wine type by its name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
			e1.printStackTrace();
			Thread.currentThread().interrupt();
			return;
		}

		//if it didn't exist, insert, otherwise, check if the whole relationship exists
		TblWinesWineType winesWineType;
		if(wineType.getId()==null)
		{
			wineType.setName(wineTypeName);
				try {
					wineType.setId(Integer.valueOf(this.requestsCreator.createPostRequest(this.properties.getProperty("crud.url"), "wineTypes?action=addWineType", this.mapper.writeValueAsString(wineType))));
				} catch (NumberFormatException e) {
					System.out.println("Id returned by the CRUD while inserting the wine type was not a proper number");
					e.printStackTrace();
					return;
				} catch (JsonProcessingException e) {
					System.out.println("An exception came up while serialising the wine type to JSON before sending it for insertion in the database");
					e.printStackTrace();
					return;
				} catch (IOException e) {
					System.out.println("While sending the wine type to the CRUD for insertion, the CRUD was not reachable");
					e.printStackTrace();
					return;
				}
			
			winesWineType = new TblWinesWineType(wine, wineType);
		}
		else
		{
			try {
				String winesWineTypeJson = this.requestsCreator.createGetRequest(this.properties.getProperty("crud.url"), "TblWinesWineTypes?action=getByWineIdAndWineTypeId"
																																		 + "&wineId="+wine.getId()
																																		 + "&wineTypeId="+wineType.getId());
				winesWineType = this.mapper.readValue(winesWineTypeJson, TblWinesWineType.class);
			} catch (JsonParseException e1) {
				System.out.println("While trying to get the possibly existing tblWinesWineType by wine id and wine type id from the CRUD, the response doesn't seem to have a valid JSON format");
				e1.printStackTrace();
				Thread.currentThread().interrupt();
				return;
			} catch (JsonMappingException e1) {
				System.out.println("While trying to get the possibly existing tblWinesWineType  by wine id and wine type id from the CRUD, the JSON response couldn't be mapped to a tblClosure object (discrepancies between model and JSON)");
				e1.printStackTrace();
				Thread.currentThread().interrupt();
				return;
			} catch (IOException e1) {
				System.out.println("While trying to get the possibly existing tblWinesWineType  by wine id and wine type id, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
				e1.printStackTrace();
				Thread.currentThread().interrupt();
				return;
			}
			
			if(winesWineType!=null)
				return;
		}

		//if we reach this point the relationship didn't exist so we insert it
		try {
			this.requestsCreator.createPostRequest(this.properties.getProperty("crud.url"), "TblWinesWineTypes?action=addTblWinesWineType", this.mapper.writeValueAsString(winesWineType));
		} catch (JsonProcessingException e) {
			System.out.println("An exception came up while serialising the tblWinesWineType to JSON before sending it for insertion in the database");
			e.printStackTrace();
			Thread.currentThread().interrupt();
			return;
		} catch (IOException e) {
			System.out.println("While sending the tblWinesWineType to the CRUD for insertion, the CRUD was not reachable");
			e.printStackTrace();
			Thread.currentThread().interrupt();
			return;
		}
	}

    /**
     * 
     * @param merchantName
     * @return
     * @throws JsonParseException
     * @throws JsonMappingException
     * @throws IOException
     */
    private tblShops getMerchant(String merchantName)
    {
    	//get merchant
    	String merchantJson;
		try {
			merchantJson = this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "Merchants", "name="+merchantName);
		} catch (IOException e) {
			System.out.println("Couldn't reach the CRUD while trying to get the merchant by its name");
			e.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		}

    	//parse json to an object and return it
		try {
			return this.mapper.readValue(merchantJson, tblShops.class);
		} catch (JsonParseException e) {
			System.out.println("While trying to get the merchant, the JSON response by the CRUD doesn't seem to have a valid format");
			e.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		} catch (JsonMappingException e) {
			System.out.println("While trying to get the merchant, the JSON response by the CRUD couldn't be mapped with a valid tblShops object");
			e.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		} catch (IOException e) {
			System.out.println("While trying to get the merchant, a low level I/O exception occurred");
			e.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		}
    }
}
