package winedunk.pf.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ejb.EJB;
import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.net.ftp.FTPClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.services.PartnersProductsService;
import winedunk.pf.services.RequestsCreator;
import winedunk.pf.services.WineService;
import winedunk.pf.models.Tblpfproduct;
import winedunk.pf.models.tblAppellations;
import winedunk.pf.models.tblPartnersProducts;
import winedunk.pf.models.tblRegions;
import winedunk.pf.models.tblShops;
import winedunk.pf.models.tblWineries;
import winedunk.pf.models.tblWines;
import winedunk.pf.helpers.NoDataFieldsValues;
import winedunk.pf.helpers.TblWineFields;
import winedunk.pf.models.Tblpfmerchanthtmlparsing;
import winedunk.pf.models.Tblpfparsingextractionmethod;

//TODO set all properties
/**
 * Servlet implementation class ProductsProcessor
 */
@WebServlet(urlPatterns="/ProductsProcessor", asyncSupported=true)
public class ProductsProcessor extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private final Properties properties;
	private final FTPClient ftp = new FTPClient();
	private final Date executionDate = new Date();
	private final ObjectMapper mapper = new ObjectMapper();
	private final RequestsCreator requestsCreator = new RequestsCreator();
	private final ExecutorService imageDownloadExecutor = Executors.newFixedThreadPool(10);

	@EJB
	private PartnersProductsService partnerProductsService;
	@EJB
	private WineService wineService;

	public ProductsProcessor() throws IOException {
        super();
        this.properties = new Properties();

        try {
        	this.ftp.connect(properties.getProperty("ftp.host.address"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
        try {
        	this.ftp.login(properties.getProperty("ftp.username"), properties.getProperty("ftp.password"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

        wineService.initialise(ftp, properties.getProperty("crud.url"));
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setStatus(HttpServletResponse.SC_ACCEPTED);

		final AsyncContext async = request.getAsyncContext();
		properties.load(new FileInputStream(new File(this.getServletContext().getRealPath("/WEB-INF/productFeed.properties"))));

		async.start(new Runnable() {

			@Override
			public void run() {
				try {
					ExecutorService executor = Executors.newFixedThreadPool(30);
	
					for(Tblpfproduct product : getProductsList(Integer.valueOf(request.getParameter("id"))))
					{
						if(product.getTblpf().getLatestStatus().getName().equals("Processing"))
						{
							//TODO check for improvement
							if(!request.getParameterMap().containsKey("id"))
							{
								continue;
							}
							else
							{
								while (product.getTblpf().getLatestStatus().getName().equals("Processing")) 
								{
									try {
										Thread.sleep(5000);
									} catch (InterruptedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
										return;
									}}
							}
						}
						executor.submit(new Runnable() {
	
							@Override
							public void run() {
								productProcess(product);
							}
							
						});
					}
					executor.shutdown();

				} finally {
					try {
						ftp.logout();
						ftp.disconnect();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		});
	}

    /**
     * 
     * @param product
     */
    private void productProcess(Tblpfproduct product)
    {
    	//TODO Map<String, String> productValues = new HashMap<String, String>();
    	//Get wine values by parsing the website
    	Map<String, String> wineValues;
		try {
			wineValues = this.parseWebsite(Jsoup.parse(new RequestsCreator().createGetRequest(product.getProductURL())),
										   this.getParsingInstructions(product.getMerchantName()));
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
			return;
		}

    	//get possibly existing wine and product
    	tblPartnersProducts partnerProduct;
		try {
			partnerProduct = this.partnerProductsService.getInstance(properties.getProperty("crud.url"), product.getPartnerProductId(), product.getMerchantProductId());
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		} catch (IOException e1) {
			// TODO Auto-generated catch block
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
		} catch (JsonParseException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		} catch (JsonMappingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}

		//set wine values
    	try {
			wine = this.setWineValues(wine, wineValues, product.getName(), product.getPartnerProductDescription(), product.getProductType());
		} catch (JsonParseException e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
		} catch (JsonMappingException e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
		} catch (IOException e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
		}

		//if the wine didn't exist previously, we will insert it to retrieve its new id and then get the image
		if(wine.getId()==null)
		{
			try {
				wine.setId(this.wineService.insertWine(wine));
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			} catch (JsonProcessingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
		}

		if(wine.getImageURL()==null /*TODO || blank "no-image" image and this product contains a valid image*/)
		{
			String finalImageName = this.wineService.getImageName(product.getImageURL(), wine.getId());
			this.imageDownloadExecutor.execute(new Runnable() {

				@Override
				public void run() {
					wineService.getImage(properties.getProperty("image.host.folder.main"), finalImageName, product.getImageURL());;
				}
				
			});
			

			wine.setImageURL(properties.getProperty("image.host.images.path")+"/"+finalImageName);
		}

		//update wine
		try {
			this.wineService.updateWine(wine);
		} catch (JsonProcessingException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
			return;
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
			return;
		}

		//set current product values
		try {
			partnerProduct = this.setPartnerProductsValues(partnerProduct, product, wine);
		} catch (JsonParseException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		} catch (JsonMappingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}

		//if we have an id it was already in the db so we update it, otherwise, we insert it
		if(partnerProduct.getId()==null)
		{
			try {
				partnerProduct.setId(this.partnerProductsService.insertProduct(partnerProduct));
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			} catch (JsonProcessingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
		}
		else
		{
			try {
				this.partnerProductsService.updateProduct(partnerProduct);
			} catch (JsonProcessingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
		}
    }

    /**
     * 
     * @param id
     * @return
     */
    private List<Tblpfproduct> getProductsList(Integer id)
    {
    	String parameters = id==null ? "action=getAll" : "action=getByPfId&pfId="+id;

		String productsJson;
		try {
			productsJson = new RequestsCreator().createPostRequest(properties.getProperty("crud.url"), "Products", parameters);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
    		Thread.currentThread().interrupt();
    		return null;
		}

		try {
			return new ObjectMapper().readValue(productsJson, new TypeReference<List<Tblpfproduct>>(){});
		} catch (JsonParseException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
    		Thread.currentThread().interrupt();
    		return null;
		} catch (JsonMappingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
    		Thread.currentThread().interrupt();
    		return null;
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
    		Thread.currentThread().interrupt();
    		return null;
		}
    }

    /**
     * 
     * @param merchantName
     * @return
     * @throws InterruptedException
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonParseException 
     */
    private List<Tblpfmerchanthtmlparsing> getParsingInstructions(String merchantName) throws InterruptedException, JsonParseException, JsonMappingException, IOException
    {
    	//Here we get the parsing instructions for each value and the html
		RequestsCreator requestsCreator = new RequestsCreator();
		ObjectMapper mapper = new ObjectMapper();
		
		//TODO crud controller and service
		tblShops merchant = this.getMerchant(merchantName);
		if(merchant==null)
		{
			Thread.currentThread().interrupt();
			return null;
		}

		//get parsing instructions by the merchant
		String merchantString;
		try {
			merchantString = requestsCreator.createPostRequest(properties.getProperty("crud.url"), "TblPfMerchantsHTMLParsing" , "action=getByMerchant&id="+merchant.getId());
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		}

		try {
			return mapper.readValue(merchantString, new TypeReference<List<Tblpfmerchanthtmlparsing>>(){});
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
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
    				//TODO check if this three tags work as expected, maybe test functionality with real html
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

    			//TODO apply filters and sanitise

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
     * @return
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonParseException 
     */
    private tblWines setWineValues(tblWines wine, Map<String, String> wineValues, String name, String description, String wineType) throws JsonParseException, JsonMappingException, IOException
    {
    	//TODO set wineType
    	//Set plain values that will not be related to another table, and thus can be just null in case we don't have them
    	wine.setDefaultDescription(description);
		wine.setName(name);
		wine.setShortDescription(description.substring(0, 161)+"...");
		wine.setDeleted(false);
		wine.setAbv(Float.valueOf(wineValues.get(TblWineFields.ABV.toString())));
		wine.setBottleSize(Float.valueOf(wineValues.get(TblWineFields.BOTTLE_SIZE.toString())));
		wine.setGtin(wineValues.get(TblWineFields.GTIN.toString()));


		//Get Closure
		if(wineValues.containsKey(TblWineFields.CLOSURE.toString()))
			wine.setClosure(this.wineService.getClosure(wineValues.get(TblWineFields.CLOSURE.toString())));
		else
			wine.setClosure(this.wineService.getClosure(NoDataFieldsValues.NO_CLOSURE.toString()));

		//Get Colour
		if(wineValues.containsKey(TblWineFields.COLOUR.toString()))
			wine.setColour(this.wineService.getColour(wineValues.get(TblWineFields.COLOUR.toString())));
		else
			wine.setColour(this.wineService.getColour(NoDataFieldsValues.NO_COLOUR.toString()));

		//Get Winery
		tblWineries winery;
		if(wineValues.containsKey(TblWineFields.WINERY.toString()))
			winery = this.wineService.getWinery(wineValues.get(TblWineFields.WINERY.toString()));
		else
			winery = this.wineService.getWinery(NoDataFieldsValues.NO_WINERY.toString());

		//Get Appellation
		tblAppellations appellation;
		if(wineValues.containsKey(TblWineFields.APPELLATION.toString()))
			appellation = this.wineService.getAppellation(TblWineFields.APPELLATION.toString());
		else if(wine.getWinery()!=null)
			appellation = this.wineService.getAppellation(wine.getWinery().getAppellationId());
		else
			appellation = this.wineService.getAppellation(NoDataFieldsValues.NO_APPELLATION.toString());

		//Get Region
		tblRegions region;
		if(wineValues.containsKey(TblWineFields.REGION.toString()))
			region = this.wineService.getRegion(wineValues.get(TblWineFields.REGION.toString()));
		else if(wine.getAppellation()!=null)
			region = wine.getAppellation().getTblRegions();
		else if(wine.getWinery()!=null)
			region = this.wineService.getRegion(wine.getWinery().getRegionId());
		else
			region = this.wineService.getRegion(NoDataFieldsValues.NO_REGION.toString());

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


		if(region.getTblCountries()==null)
		{
			region.setTblCountries(wine.getCountry());
			if(region.getId()==null)
			{
				String id = this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "regions?action=addRegion", this.mapper.writeValueAsString(region));
				
				region.setId(Integer.valueOf(id));
			}
			else
			{
				this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "regions?action=updateRegion", this.mapper.writeValueAsString(region));
			}
		}
		wine.setRegion(region);

		if(appellation.getTblCountries()==null || appellation.getTblRegions()==null)
		{
			if(appellation.getTblCountries()==null)
				appellation.setTblCountries(wine.getCountry());
			if(appellation.getTblRegions()==null)
				appellation.setTblRegions(wine.getRegion());
			
			if(appellation.getId()==null)
			{
				String id = this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "appellations?action=addAppellation", this.mapper.writeValueAsString(appellation));
				
				appellation.setId(Integer.valueOf(id));
			}
			else
			{
				this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "appellations?action=updateAppellation", this.mapper.writeValueAsString(appellation));
			}
		}

		wine.setAppellation(appellation);

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
				String id = this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "wineries?action=addWinery", this.mapper.writeValueAsString(winery));
				
				winery.setId(Integer.valueOf(id));
			}
			else
			{
				this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "wineries?action=updateWinery", this.mapper.writeValueAsString(winery));
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
    private tblPartnersProducts setPartnerProductsValues(tblPartnersProducts partnersProducts, Tblpfproduct productStandard, tblWines wine) throws JsonParseException, JsonMappingException, IOException
    {
    	partnersProducts.setDeleted(false)
    					.setDeletedDate(null)
    					.setLastUpdated(this.executionDate)
    					.setPartnerDestinationUrl(productStandard.getClicktag())
    					//TODO .setPartnerId(productStandard.getTblpf())
    					.setPartnerMerchantDeliveringCost(productStandard.getDeliveryCost())
    					//TODO .setPartnerMerchantId(partnerMerchantId)
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
     * @param merchantName
     * @return
     * @throws JsonParseException
     * @throws JsonMappingException
     * @throws IOException
     */
    private tblShops getMerchant(String merchantName) throws JsonParseException, JsonMappingException, IOException
    {
    	//get merchant
    	String merchantJson = this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "Merchants", "name="+merchantName);
    	//parse json to an object and return it
		return this.mapper.readValue(merchantJson, tblShops.class);
    }
}