package winedunk.pf.runnables;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.WordUtils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.helpers.Colours;
import winedunk.pf.helpers.NoDataFieldsValues;
import winedunk.pf.helpers.TblWineFields;
import winedunk.pf.models.DataSource;
import winedunk.pf.models.TblWinesGrapeVariety;
import winedunk.pf.models.TblWinesWineType;
import winedunk.pf.models.Tblpfmerchanthtmlparsing;
import winedunk.pf.models.Tblpfproduct;
import winedunk.pf.models.tblAppellations;
import winedunk.pf.models.tblClosures;
import winedunk.pf.models.tblColours;
import winedunk.pf.models.tblGrapeVarieties;
import winedunk.pf.models.tblPartnersMerchants;
import winedunk.pf.models.tblPartnersProducts;
import winedunk.pf.models.tblRegions;
import winedunk.pf.models.tblShops;
import winedunk.pf.models.tblWineTypes;
import winedunk.pf.models.tblWineries;
import winedunk.pf.models.tblWines;
import winedunk.pf.services.DataExtractor;
import winedunk.pf.services.HtmlDataExtractor;
import winedunk.pf.services.JsonDataExtractor;
import winedunk.pf.services.PartnersProductsService;
import winedunk.pf.services.ProductService;
import winedunk.pf.services.RequestsCreator;
import winedunk.pf.services.WineService;
import winedunk.pf.services.XmlDataExtractor;

public class ProductsProcessRunnable implements Callable<Integer>{

	private final ObjectMapper mapper = new ObjectMapper();
	private final Properties properties;
	private final Date executionDate;
	private Tblpfproduct product;
	private WineService wineService;
	private PartnersProductsService partnersProductsService;

	private Integer j;

	/**
	 * 
	 * @param product
	 * @param properties
	 * @param executionDate
	 */
	public ProductsProcessRunnable(Integer j, Properties properties, Date executionDate, Tblpfproduct product)
	{
		this.properties = properties;
		this.executionDate = executionDate;
		this.product = product;
		this.j = j;
		this.mapper.setSerializationInclusion(Include.NON_NULL);
	}

	/**
	 * <p>Default call method that will run in background automatically and return the product feed id of this product, that will be stored in a list on the main asynchronous thread on @see winedunk.pf.controllers.ProductsProcessor#doGet()</p>
	 * <p>We will use that list later to count how many wines have been processed </p>
	 * 
	 */
	@Override
	public Integer call() {
		try {
			System.out.println("Processing wine number "+j);
			Integer id = productProcess(product);
			System.out.println("Wine "+j+" processed");
			return id;
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
     * 
     * @param product
     */
    private Integer productProcess(Tblpfproduct product)
    {
    	this.wineService = new WineService(properties);
		this.partnersProductsService = new PartnersProductsService(properties.getProperty("crud.url"));
		try {
	    	System.out.println(product.getName() + " - " + product.getMerchantName());
	    	//Get wine values by parsing the website
	    	Map<String, String> wineValues=null;
			try {
				//System.out.println("GET MERCHANT PARSING");
				List<Tblpfmerchanthtmlparsing> merchantParsing = this.getParsingInstructions(product.getMerchantName());

				if(merchantParsing.isEmpty())
					System.out.println("No parsing for merchant "+product.getMerchantName());

				wineValues = this.getWineValues(product, merchantParsing);

			/*} catch (JsonParseException e4) {
				System.out.println("While trying to get the merchant by name from the CRUD, response doesn't seem to have a valid JSON format");
				e4.printStackTrace();
			} catch (JsonMappingException e4) {
				System.out.println("While trying to get the merchant by name from the CRUD, response doesn't look like a serialised merchant");
				e4.printStackTrace();
			} catch (IOException e4) {
				System.out.println("Exception occurred while reaching CRUD");
				e4.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			*/} finally {
				if(wineValues==null)
					wineValues = new HashMap<String, String>();
			}

			//Extract wine colour from wine type
			//System.out.println("Extract wine colour from wine type");
			if(!wineValues.containsKey(TblWineFields.COLOUR))
			{
				if(StringUtils.containsIgnoreCase(product.getProductType(), Colours.RED))
					wineValues.put(TblWineFields.COLOUR, Colours.RED);
				else if(StringUtils.containsIgnoreCase(product.getProductType(), Colours.WILDCARD_ROSE))
					wineValues.put(TblWineFields.COLOUR, Colours.ROSE);
				else if(StringUtils.containsIgnoreCase(product.getProductType(), Colours.WHITE))
					wineValues.put(TblWineFields.COLOUR, Colours.WHITE);
			}

			//get possibly existing wine and product
			//System.out.println("get possibly existing wine and product");
	    	tblPartnersProducts partnerProduct;
			try {
				// aripe 2018-03-31
				partnerProduct = this.partnersProductsService.getProduct(product.getTblpf().getPartnerId().getId(), product.getPartnerProductId());
				
				// partnerProduct = this.partnersProductsService.getProduct(product.getPartnerProductId(), product.getMerchantProductId());
			} catch (IOException e1) {
				System.out.println("While trying to find a possible copy of the product already existing in the db (and retrieve it for edition) the CRUD wasn't reachable or there was a low-level I/O exception, please check the server");
				e1.printStackTrace();
				return null;
			}

			if(partnerProduct.getLastUpdated()!=null && product.getTblpf().getLastStandardisation()!=null)
			{
				if(partnerProduct.getLastUpdated().getTime()>product.getTblpf().getLastStandardisation().getTime())
					return null;
			}

			//if we have the wine in the product, we get it, otherwise, we instanciate a new one by different methods (looking for possibly already existing wine)
			tblWines wine = new tblWines();

			//set wine values
	    	wine = this.setWineValues(wine, wineValues, product.getName(), product.getPartnerProductDescription());
	
	    	if(Thread.currentThread().isInterrupted())
	    	{
	    		System.out.println("An exception caused this thread to be interrupted while generating wine "+product.getName());
	    		return null;
	    	}

	    	//System.out.println("GETTING DATA FROM WINE NAME");
			//Sanitise the name removing unwanted details and extract those details as possible values  for other fields in the tblWines table
			try {
				wine = this.wineService.completeDataFromName(wine, product.getMerchantName());
			} catch(Exception e) {
				e.printStackTrace();
				return null;
			}

			tblWines existingWine;
	    	try {
	    		existingWine = partnerProduct.getTblWines() != null ? partnerProduct.getTblWines() : this.wineService.getInstance(wine.getName(),
																							 							  wineValues.get(TblWineFields.GTIN),
																							 							  wineValues.get(TblWineFields.BOTTLE_SIZE),
																							 							  wineValues.get(TblWineFields.VINTAGE));
			} catch (JsonParseException e4) {
				System.out.println("While trying to find the possibly existing wine, the JSON response from the CRUD doesn't seem to have a valid format");
				e4.printStackTrace();
				return null;
			} catch (JsonMappingException e4) {
				System.out.println("While trying to find the possibly existing wine, the JSON response from the CRUD doesn't seem to have a valid format");
				e4.printStackTrace();
				return null;
			} catch (IOException e4) {
				System.out.println("While trying to find the possibly existing wine, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
				e4.printStackTrace();
				return null;
			}

	    	if(existingWine!=null)
	    		wine = this.wineService.mergeWines(wine, existingWine);
	    	//if the wine didn't exist previously, we will insert it to retrieve its new id and then get the image
			if(wine.getId()==null)
			{
				try {
					wine.setId(this.wineService.insertWine(wine));
				} catch (NumberFormatException e) {
					System.out.println("Id returned by the CRUD while inserting the wine was not a proper number");
					e.printStackTrace();
					return null;
				} catch (JsonProcessingException e) {
					System.out.println("An exception came up while serialising the wine to JSON before sending it for insertion in the database");
					e.printStackTrace();
					return null;
				} catch (IOException e) {
					System.out.println("While sending the wine to the CRUD for insertion, the CRUD was not reachable");
					e.printStackTrace();
					return null;
				}
			}

			//System.out.println("SETTING WINE GRAPE VARIETY");
	    	if(!StringUtils.isBlank(wineValues.get(TblWineFields.WINE_GRAPEVARIETY)))
	    	{
	    		String[] grapeVarieties = wineValues.get(TblWineFields.WINE_GRAPEVARIETY).split(",|-|\\s+and\\s+");
	        	for(String grapeVariety : grapeVarieties)
	        	{
	        		if(StringUtils.isBlank(grapeVariety))
	        			continue;
	        		wine = this.setWinesGrapeVarieties(grapeVariety.trim(), wine);
	        	}
	    	}

	    	//System.out.println("GETTING IMAGE");
			if(wine.getImageURL()==null /*TODO || blank "no-image" image and this product contains a valid image*/)
			{
				String finalImageName = this.wineService.getImageName(product.getImageURL(), wine.getId());
				wineService.getImage(finalImageName, product.getImageURL());			

				wine.setImageURL(properties.getProperty("images.host.url")+"/"+finalImageName);
			}

			//Work out the wine type
			//System.out.println("SETTING WINE TYPE");
			if(!StringUtils.isBlank(wineValues.get(TblWineFields.WINE_TYPE)) && wineValues.get(TblWineFields.WINE_TYPE).length()<=100)
				this.setWinesWineType(wine, wineValues.get(TblWineFields.WINE_TYPE));
			else if(!StringUtils.isBlank(product.getProductType()))
				this.setWinesWineType(wine, product.getProductType());
			else
				this.setWinesWineType(wine, NoDataFieldsValues.NO_WINETYPE);
			//System.out.println("WINE TYPE SET");
	
			//System.out.println("UPDATING WINE");
			//update wine
			try {
				this.wineService.updateWine(wine);
			} catch (JsonProcessingException e2) {
				System.out.println("An exception came up while serialising the wine to JSON before sending it for update in the database");
				e2.printStackTrace();
				return null;
			} catch (IOException e2) {
				System.out.println("While sending the wine to the CRUD for update, the CRUD was not reachable");
				e2.printStackTrace();
				return null;
			}

			//try {
			//if we have an id it was already in the db so we update it, otherwise, we insert it
			if(partnerProduct.getId()==null)
			{
				//set current product values
				partnerProduct = this.setPartnerProductsValues(partnerProduct, product, wine);
				//System.out.println("INSERTING PRODUCT");
				try {
					partnerProduct.setId(this.partnersProductsService.insertProduct(partnerProduct));
				} catch (NumberFormatException e) {
					System.out.println("Id returned by the CRUD while inserting the product was not a proper number");
					e.printStackTrace();
					return null;
				} catch (JsonProcessingException e) {
					System.out.println("An exception came up while serialising the product to JSON before sending it for insertion in the database");
					e.printStackTrace();
					return null;
				} catch (IOException e) {
					System.out.println("While sending the product to the CRUD for insertion, the CRUD was not reachable");
					e.printStackTrace();
					return null;
				}
			}
			else
			{
				//System.out.println("UPDATING PRODUCT");
				try {
					this.partnersProductsService.updateProduct(partnerProduct.getId(), product.getPrice());
				} catch (JsonProcessingException e2) {
					System.out.println("An exception came up while serialising the product to JSON before sending it for update in the database");
					e2.printStackTrace();
					return null;
				} catch (IOException e2) {
					System.out.println("While sending the product to the CRUD for update, the CRUD was not reachable");
					e2.printStackTrace();
					return null;
				}
			}
			return partnerProduct.getId();
		} catch(Exception e) {
    		e.printStackTrace();
    		return null;
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
    	try {
    		// aripe 2018-04-05
			//tblShops merchant = this.getMerchant(merchantName);
    		tblShops merchant = this.getMerchantBypartnerMerchantName(merchantName).getShop();
    		
			if(merchant.getId()==null)
			{
				System.out.println("Couldn't find merchant, so no parsing data can be provided");
				return new ArrayList<Tblpfmerchanthtmlparsing>();
			}

			//get parsing instructions by the merchant
			String merchantParsingString;
			try {
				merchantParsingString = RequestsCreator.createPostRequest(this.properties.getProperty("crud.url"), "TblPfMerchantsHTMLParsing?action=getByMerchant" , "{ \"id\" : "+merchant.getId()+" }", null);
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Couldn't reach CRUD destination while attempting to get the list of parsing instructions");
				return new ArrayList<Tblpfmerchanthtmlparsing>();
			}
			
			if(StringUtils.isBlank(merchantParsingString))
			{
				System.out.println("Could find the merchant, but not the mapping! Skipping");
				return new ArrayList<Tblpfmerchanthtmlparsing>();
			}
	
			try {
				return this.mapper.readValue(merchantParsingString, new TypeReference<List<Tblpfmerchanthtmlparsing>>(){});
			} catch (JsonParseException e) {
				System.out.println("While trying to get the merchant parsing instructions, the JSON response by the CRUD doesn't seem to have a valid format");
				e.printStackTrace();
			} catch (JsonMappingException e) {
				System.out.println("While trying to get the merchant parsing instructions, the JSON response by the CRUD couldn't be mapped with a valid Tblpfmerchanthtmlparsing object");
				e.printStackTrace();
			} catch (IOException e) {
				System.out.println("While trying to get the merchant parsing instructions, a low level I/O exception occurred");
				e.printStackTrace();
			}
    	} catch(Exception e) {
    		e.printStackTrace();
    	}

		return new ArrayList<Tblpfmerchanthtmlparsing>();
    }

    /**
     * @param product The product we are parsing
     * @param merchantParsings The list of elements we can parse with instructions for doing so
     * @return A map containing the values, being the Key the name of the column in tblWines and the value, its own value 
     * @throws Exception 
     */
    private Map<String, String> getWineValues(Tblpfproduct product, List<Tblpfmerchanthtmlparsing> merchantParsings) throws Exception
    {
		// aripe 2018-04-05
		// DataSource dataSource = this.getMerchant(product.getMerchantName()).getDataSource();
    	DataSource dataSource = this.getMerchantBypartnerMerchantName(product.getMerchantName()).getShop().getDataSource();

    	ProductService productService = new ProductService();

    	DataExtractor dataExtractor;
    	System.out.println(dataSource==null);
    	switch(dataSource.getContentType())
    	{
	    	case HTML:
	    		System.out.println("It's html");
	    		dataExtractor = new HtmlDataExtractor();
	    		break;
	    	case JSON:
	    		dataExtractor = new JsonDataExtractor();
	    		break;
	    	case XML:
	    		dataExtractor = new XmlDataExtractor();
	    		break;
    		default:
    			throw new Exception("Unrecognized content type");
    	}

    	String token = dataExtractor.login(dataSource.getLoginUrl(), dataSource.getTokenField());
    	Map<String, String> authHeader = new HashMap<String, String>();
    	if(token!=null)
    	{
    		authHeader.put(dataSource.getAuthField(), token);
    	}

    	String dataUrl = productService.getFinalProductUrl(product, dataSource);

    	return dataExtractor.parseWineData(dataUrl, authHeader, product, merchantParsings);
    }

    /**
     * 
     * @param wine
     * @param wineValues
     * @param name
     * @param description
     * @return
     */
    private tblWines setWineValues(tblWines wine, Map<String, String> wineValues, String name, String description)
    {
    	description = description.replaceAll("\\r | \\n", ". ");

    	try {
	    	//Set name
			wine.setName(StringEscapeUtils.unescapeHtml4(name));
	
			/*
			 * Just checking which one is bigger would be easier but this way we avoid possible null pointer exceptions
			 * As the AND condition stops checking any more conditions once one is false, if it's null it won't check the length and we will avoid the exception 
			 */

			if((wine.getDefaultDescription()!=null && description.length()>wine.getDefaultDescription().length()) || wine.getDefaultDescription()==null)
			{
				wine.setDefaultDescription(description);
				wine.setShortDescription(description.length()>160 ? (description.substring(0, 161)+"...") : description);
			}

			wine.setDeleted(false);
			wine.setAbv(NumberUtils.isCreatable(wineValues.get(TblWineFields.ABV)) ? Float.parseFloat(wineValues.get(TblWineFields.ABV)) : null);
			wine.setBottleSize(NumberUtils.isCreatable(wineValues.get(TblWineFields.BOTTLE_SIZE)) ? Float.parseFloat(wineValues.get(TblWineFields.BOTTLE_SIZE)) : null);
			wine.setVintage(NumberUtils.isCreatable(wineValues.get(TblWineFields.VINTAGE)) ? Integer.parseInt(wineValues.get(TblWineFields.VINTAGE)) : null);
			wine.setGtin(wineValues.get(TblWineFields.GTIN));

			//Get Closure
			tblClosures closure;
			try {
				if(!StringUtils.isBlank(wineValues.get(TblWineFields.CLOSURE)))
					closure = this.wineService.getClosure(WordUtils.capitalizeFully(StringUtils.stripAccents(wineValues.get(TblWineFields.CLOSURE))));
				else
					closure = this.wineService.getClosure(NoDataFieldsValues.NO_CLOSURE);
			} catch (JsonParseException e1) {
				System.out.println("While trying to get the closure by its name from the CRUD, the response doesn't seem to have a valid JSON format");
				e1.printStackTrace();
				return null;
			} catch (JsonMappingException e1) {
				System.out.println("While trying to get the closure by its name from the CRUD, the JSON response couldn't be mapped to a tblClosure object (discrepancies between model and JSON)");
				e1.printStackTrace();
				return null;
			} catch (IOException e1) {
				System.out.println("While trying to get the closure by its name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
				e1.printStackTrace();
				return null;
			}

			if(closure.getId()==null)
			{
				closure.setDeleted(false);
				closure.setId(Integer.parseInt(RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "closures?action=addClosure", this.mapper.writeValueAsString(closure), null)));
			}
	
			wine.setClosure(closure);

			
			//Get Colour
			tblColours colour;
			try {
				if(!StringUtils.isBlank(wineValues.get(TblWineFields.COLOUR)))
				{
					String colourName = wineValues.get(TblWineFields.COLOUR);
					if(StringUtils.containsIgnoreCase(colourName, Colours.RED))
						colourName = Colours.RED;
					else if(StringUtils.containsIgnoreCase(colourName, Colours.WHITE))
						colourName = Colours.WHITE;
					else if(StringUtils.containsIgnoreCase(colourName, Colours.WILDCARD_ROSE))
						colourName = Colours.ROSE;					
					
					if(colourName.length()<46)
						colour = this.wineService.getColour(WordUtils.capitalizeFully(StringUtils.stripAccents(colourName)));
					else
						colour = this.wineService.getColour(NoDataFieldsValues.NO_COLOUR);
				}
				else
					colour = this.wineService.getColour(NoDataFieldsValues.NO_COLOUR);
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

			if(colour.getId()==null)
			{
				colour.setDeleted(false);
				colour.setId(Integer.parseInt(RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "colours?action=addColour", this.mapper.writeValueAsString(colour), null)));
			}

			wine.setColour(colour);

			//Get Winery
			tblWineries winery;
			try {
				winery = this.wineService.getWinery(wineValues.getOrDefault(TblWineFields.COUNTRY, ""), wineValues.getOrDefault(TblWineFields.REGION, ""), wineValues.getOrDefault(TblWineFields.APPELLATION, ""), wineValues.getOrDefault(TblWineFields.WINERY, NoDataFieldsValues.NO_WINERY));
			} catch (JsonParseException e1) {
				System.out.println("While trying to get the winery by its name from the CRUD, the response doesn't seem to have a valid JSON format");
				e1.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (JsonMappingException e1) {
				System.out.println("While trying to get the winery by its name from the CRUD, the JSON response couldn't be mapped to a tblWineries object (discrepancies between model and JSON)");
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
			if(!StringUtils.isBlank(wineValues.get(TblWineFields.APPELLATION)))
			{
				try {
					appellation = this.wineService.getAppellation(wineValues.get(TblWineFields.APPELLATION));
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
					appellation = this.wineService.getAppellation(NoDataFieldsValues.NO_APPELLATION);
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
			if(!StringUtils.isBlank(wineValues.get(TblWineFields.REGION)) && wineValues.get(TblWineFields.REGION).length()<=45)
			{
				//Get region by name with the value extracted from the website
				try {
					region = this.wineService.getRegion(wineValues.get(TblWineFields.REGION));
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
				region = wine.getAppellation().getRegionId();
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
					region = this.wineService.getRegion(NoDataFieldsValues.NO_REGION);
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
			if(!StringUtils.isBlank(wineValues.get(TblWineFields.COUNTRY)))
				wine.setCountry(this.wineService.getCountry(wineValues.get(TblWineFields.COUNTRY)));
			else if(wine.getRegion()!=null)
				wine.setCountry(wine.getRegion().getTblCountries());
			else if(wine.getAppellation()!=null)
				wine.setCountry(wine.getAppellation().getCountryId());
			else if(wine.getWinery()!=null)
				wine.setCountry(wine.getWinery().getTblCountries());
			else
				wine.setCountry(this.wineService.getCountry(NoDataFieldsValues.NO_COUNTRY));

			if(wine.getCountry().getId()==null)
			{
				wine.getCountry().setDeleted(false);
				wine.getCountry().setId(Integer.parseInt(RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "countries?action=addCountry", this.mapper.writeValueAsString(wine.getCountry()), null)));
			}

			//Add country to the region if necessary and then insert or update it 
			if(region.getTblCountries()==null)
			{
				region.setTblCountries(wine.getCountry());
				
				if(region.getId()==null)
				{
					try {
						System.out.println(region);
						region.setId(Integer.valueOf(RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "regions?action=addRegion", this.mapper.writeValueAsString(region), null)));
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
				}
				else
				{
					try {
						RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "regions?action=updateRegion", this.mapper.writeValueAsString(region), null);
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
			if(appellation.getCountryId()==null || appellation.getCountryId()==null)
			{
				if(appellation.getCountryId()==null)
					appellation.setCountryId(wine.getCountry());
				if(appellation.getRegionId()==null)
					appellation.setRegionId(wine.getRegion());
	
				if(appellation.getId()==null)
				{
					String id;
					try {
						id = RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "appellations?action=addAppellation", this.mapper.writeValueAsString(appellation), null);
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
						RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "appellations?action=updateAppellation", this.mapper.writeValueAsString(appellation), null);
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
					System.out.println(winery);
					String id;
					try {
						id = RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "wineries?action=addWinery", this.mapper.writeValueAsString(winery), null);
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
						RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "wineries?action=updateWinery", this.mapper.writeValueAsString(winery), null);
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
    	} catch (Exception e) {
    		e.printStackTrace();
    		return null;
    	}
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
    					.setPartnerId(productStandard.getTblpf().getPartnerId())
    					.setPartnerMerchantDeliveringCost(productStandard.getDeliveryCost())
    					.setPartnerMerchantId(productStandard.getPartnerMerchantId())
    					.setPartnerMerchantProductId(productStandard.getMerchantProductId())
    					.setPartnerMerchantStock(null)
    					.setPartnerProductId(productStandard.getPartnerProductId())
    					.setPartnerProductPrice(productStandard.getPrice())
    					
    					
    					// aripe 2018-04-05
    					// .setShopId(this.getMerchant(productStandard.getMerchantName()))
    					.setShopId(this.getMerchantBypartnerMerchantName(productStandard.getMerchantName()).getShop())
    					
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
    	wineTypeName = WordUtils.capitalizeFully(StringUtils.stripAccents(wineTypeName));
    	String encodedWineTypeName;
    	try {
			encodedWineTypeName = URLEncoder.encode(wineTypeName, "UTF-8");
		} catch (UnsupportedEncodingException e2) {
			System.out.println("Error while encoding wineTypeName using UTF-8");
			e2.printStackTrace();
			return;
		}
		//take wine type
		tblWineTypes wineType;
		try {
			String wineTypeJson;
			wineTypeJson = RequestsCreator.createGetRequest(this.properties.getProperty("crud.url"), "WineTypesMapping?action=getByWineType&type="+encodedWineTypeName, null);
			if(wineTypeJson.isEmpty())
				wineTypeJson = RequestsCreator.createGetRequest(this.properties.getProperty("crud.url"), "winetypes?action=getByName&name="+encodedWineTypeName, null);
			wineType = this.mapper.readValue(wineTypeJson, tblWineTypes.class);
		} catch (JsonParseException e1) {
			System.out.println("While trying to get the possibly existing wine type by its name from the CRUD, the response doesn't seem to have a valid JSON format");
			e1.printStackTrace();
			return;
		} catch (JsonMappingException e1) {
			System.out.println("While trying to get the possibly existing wine type by its name from the CRUD, the JSON response couldn't be mapped to a tblWineType object (discrepancies between model and JSON)");
			e1.printStackTrace();
			return;
		} catch (IOException e1) {
			System.out.println("While trying to get the possibly existing wine type by its name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
			e1.printStackTrace();
			return;
		}

		//if it didn't exist, insert, otherwise, check if the whole relationship exists
		TblWinesWineType winesWineType;
		if(wineType.getId()==null)
		{
			wineType.setName(wineTypeName);
			wineType.setDeleted(false);
			//System.out.println(wineType);
			String response = null;
			try {
				//System.out.println(this.mapper.writeValueAsString(wineType));
				response = RequestsCreator.createPostRequest(this.properties.getProperty("crud.url"), "winetypes?action=addWineType", this.mapper.writeValueAsString(wineType), null);
				//System.out.println(response);
				wineType.setId(Integer.parseInt(response));
			} catch (NumberFormatException e) {
				System.out.println("Id returned by the CRUD while inserting the wine type was not a proper number: "+response);
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
		}
		else
		{
			try {
				String winesWineTypeJson = RequestsCreator.createGetRequest(this.properties.getProperty("crud.url"), 
																			"TblWinesWineTypes?action=getByWineIdAndWineTypeId" + "&wineId="+wine.getId() + "&wineTypeId="+wineType.getId(),
																			null);
				winesWineType = this.mapper.readValue(winesWineTypeJson, TblWinesWineType.class);
			} catch (JsonParseException e1) {
				System.out.println("While trying to get the possibly existing tblWinesWineType by wine id and wine type id from the CRUD, the response doesn't seem to have a valid JSON format");
				e1.printStackTrace();
				return;
			} catch (JsonMappingException e1) {
				System.out.println("While trying to get the possibly existing tblWinesWineType  by wine id and wine type id from the CRUD, the JSON response couldn't be mapped to a tblClosure object (discrepancies between model and JSON)");
				e1.printStackTrace();
				return;
			} catch (IOException e1) {
				System.out.println("While trying to get the possibly existing tblWinesWineType  by wine id and wine type id, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
				e1.printStackTrace();
				return;
			}
			
			if(winesWineType!=null)
				return;
		}

		//if we reach this point the relationship didn't exist so we populate the values insert it
		winesWineType = new TblWinesWineType(wine, wineType);

		try {
			RequestsCreator.createPostRequest(this.properties.getProperty("crud.url"), "TblWinesWineTypes?action=addTblWinesWineType", this.mapper.writeValueAsString(winesWineType), null);
		} catch (JsonProcessingException e) {
			System.out.println("An exception came up while serialising the tblWinesWineType to JSON before sending it for insertion in the database");
			e.printStackTrace();
			return;
		} catch (IOException e) {
			System.out.println("While sending the tblWinesWineType to the CRUD for insertion, the CRUD was not reachable");
			e.printStackTrace();
			return;
		}
	}

    public tblWines setWinesGrapeVarieties(String grapeVarietyName, tblWines wine)
    {
		grapeVarietyName = WordUtils.capitalizeFully(StringUtils.stripAccents(grapeVarietyName));

    	String grpVarietyJson;
		try {
			grpVarietyJson = RequestsCreator.createGetRequest(properties.getProperty("crud.url"), "grapevarieties?action=getByName&name="+URLEncoder.encode(grapeVarietyName, "UTF-8"), null);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return wine;
		}

		tblGrapeVarieties grapeVariety;
		try {
			grapeVariety = this.mapper.readValue(grpVarietyJson, tblGrapeVarieties.class);
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return wine;
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return wine;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return wine;
		}

		if(grapeVariety.getId()==null)
		{
			grapeVariety.setName(grapeVarietyName);
				try {
					grapeVariety.setId(Integer.parseInt(RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "grapevarieties?action=addGrapeVariety", this.mapper.writeValueAsString(grapeVariety), null)));
				} catch (NumberFormatException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return wine;
				} catch (JsonProcessingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return wine;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return wine;
				}
		}

		TblWinesGrapeVariety wineGrapeVariety = new TblWinesGrapeVariety();
		wineGrapeVariety.setGrapeVariety(grapeVariety);
		wineGrapeVariety.setWine(wine);
System.out.println(wine.getTblWinesGrapeVariety());
		wine.addTblWinesGrapeVariety(wineGrapeVariety);
		System.out.println(wine.getTblWinesGrapeVariety());
		return wine;
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
			merchantJson = RequestsCreator.createGetRequest(properties.getProperty("crud.url"), "shops?action=getByName&name="+URLEncoder.encode(merchantName, "UTF-8"), null);
		} catch (IOException e) {
			System.out.println("Couldn't reach the CRUD while trying to get the merchant by its name");
			e.printStackTrace();
			return new tblShops();
		}

		//parse json to an object and return it
		try {
			return this.mapper.readValue(merchantJson, tblShops.class);
		} catch (JsonParseException e) {
			System.out.println("While trying to get the merchant, the JSON response by the CRUD doesn't seem to have a valid format");
			e.printStackTrace();
		} catch (JsonMappingException e) {
			System.out.println("While trying to get the merchant, the JSON response by the CRUD couldn't be mapped with a valid tblShops object");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("While trying to get the merchant, a low level I/O exception occurred");
			e.printStackTrace();
		}
		return new tblShops();
    }
    
    private tblPartnersMerchants getMerchantBypartnerMerchantName(String partnerMerchantName)
    {
		// aripe 2018-04-05, new table `tblPartnersMerchants` created to store partner merchants info (tblShops might contain different names)
    	String merchantJson;
		try {
			
			merchantJson = RequestsCreator.createGetRequest(properties.getProperty("crud.url"), "partnersMerchants?action=getPartnersMerchantsBypartnerMerchantName&partnerMerchantName="+URLEncoder.encode(partnerMerchantName, "UTF-8"), null);
			
		} catch (IOException e) {
			System.out.println("Couldn't reach the CRUD while trying to get \"getMerchantBypartnerMerchantName(" + partnerMerchantName + ")\"");
			e.printStackTrace();
			return new tblPartnersMerchants();
		}

		//parse json to an object and return it
		try {
			return this.mapper.readValue(merchantJson, tblPartnersMerchants.class);
		} catch (JsonParseException e) {
			System.out.println("While trying to get the MerchantBypartnerMerchantName, the JSON response by the CRUD doesn't seem to have a valid format");
			e.printStackTrace();
		} catch (JsonMappingException e) {
			System.out.println("While trying to get the MerchantBypartnerMerchantName, the JSON response by the CRUD couldn't be mapped with a valid tblShops object");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("While trying to get the MerchantBypartnerMerchantName, a low level I/O exception occurred");
			e.printStackTrace();
		}
		return new tblPartnersMerchants();
    }
    
}