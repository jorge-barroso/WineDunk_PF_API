package winedunk.pf.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import winedunk.pf.models.tblPartnersProducts;
import winedunk.pf.models.tblShops;
import winedunk.pf.models.tblWines;
import winedunk.pf.helpers.tblWineFields;
import winedunk.pf.models.Tblpfmerchanthtmlparsing;
import winedunk.pf.models.Tblpfparsingextractionmethod;

/**
 * Servlet implementation class ProductsProcessor
 */
@WebServlet(urlPatterns="/ProductsProcessor", asyncSupported=true)
public class ProductsProcessor extends HttpServlet {
	private static final long serialVersionUID = 1L;
	final Properties properties;
	final Map<String, String> countries;
	final FTPClient ftp;

	@EJB
	WineService wineService;
	@EJB
	PartnersProductsService partnerProductsService;

	public ProductsProcessor() {
        super();
        this.properties = new Properties();
		this.countries = new HashMap<String, String>();

        for(String countryCode : Locale.getISOCountries())
		{
        	this.countries.put(new Locale("", countryCode).getDisplayCountry(), countryCode);
		}

        this.ftp = new FTPClient();
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
		
		//if we have the wine in the product, we get it, otherwise, we instanciate a new one by different methods 
		tblWines wine = partnerProduct.getTblWines() != null ? partnerProduct.getTblWines() : this.wineService.getInstance(properties.getProperty("crud.url"),
																				 										   product.getName(),
																				 										   wineValues.get(tblWineFields.GTIN.toString()),
																				 										   wineValues.get(tblWineFields.BOTTLE_SIZE.toString()),
																				 										   wineValues.get(tblWineFields.VINTAGE.toString()));

		//set wine values
    	wine = this.setWineValues(wine, wineValues, product.getName(), product.getPartnerProductDescription(), product.getProductType());

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
			try {
				String finalImageName = this.wineService.getImage(this.ftp, properties.getProperty("image.host.folder.main"), wine.getImageURL(), wine.getId());

				wine.setImageURL(properties.getProperty("image.host.images.path")+"/"+finalImageName);
			} catch (IOException e) {
				// TODO Auto-generated catch block getImage failed
				e.printStackTrace();
				return;
			}
		}

		try {
			new RequestsCreator().createPostRequest(properties.getProperty("crud.url"), "wines?action=updateWine", new ObjectMapper().writeValueAsString(wine));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

		//TODO product
		if(partnerProduct.getId()==null)
		{
			try {
				partnerProduct.setId(this.partnerProductsService.insertProduct(partnerProduct));
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JsonProcessingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		partnerProduct = this.setPartnerProductsValues();
		
		try {
			this.partnerProductsService.updateProduct(partnerProduct);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
     * @param merchantName
     * @return
     * @throws InterruptedException
     */
    private List<Tblpfmerchanthtmlparsing> getParsingInstructions(String merchantName) throws InterruptedException
    {
    	//Here we get the parsing instructions for each value and the html
		RequestsCreator requestsCreator = new RequestsCreator();
		ObjectMapper mapper = new ObjectMapper();
		
		//TODO crud controller and service
		//get merchant
		tblShops merchant;
		try {
			merchant = mapper.readValue(requestsCreator.createPostRequest(properties.getProperty("crud.url"), "Merchants", "name="+merchantName), tblShops.class);
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
			Thread.currentThread().interrupt();
			return null;
		}
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
     * @param wine
     * @param wineValues
     * @param name
     * @param description
     * @param wineType
     * @return
     */
    private tblWines setWineValues(tblWines wine, Map<String, String> wineValues, String name, String description, String wineType)
    {
    	//TODO set wineType
    	wine.setAbv(Float.valueOf(wineValues.get(tblWineFields.ABV.toString())));
		wine.setBottleSize(Float.valueOf(wineValues.get(tblWineFields.BOTTLE_SIZE.toString())));
		wine.setDefaultDescription(description);
		wine.setName(name);
		wine.setShortDescription(description.substring(0, 161)+"...");
		wine.setGtin(wineValues.get(tblWineFields.GTIN.toString()));
		wine.setDeleted(false);
		wine.setClosure(this.wineService.getClosure(wineValues.get(tblWineFields.CLOSURE.toString())));
		wine.setColour(this.wineService.getColour(wineValues.get(tblWineFields.COLOUR.toString())));

		if(wineValues.containsKey(tblWineFields.WINERY.toString()))
			wine.setWinery(this.wineService.getWinery(wineValues.get(tblWineFields.WINERY.toString())));

		if(wineValues.containsKey(tblWineFields.APPELLATION.toString()))
			wine.setAppellation(this.wineService.getAppellation(tblWineFields.APPELLATION.toString()));
		else if(wine.getWinery()!=null)
			wine.setAppellation(this.wineService.getAppellation(wine.getWinery().getAppellationId()));

		if(wineValues.containsKey(tblWineFields.REGION.toString()))
			wine.setRegion(this.wineService.getRegion(wineValues.get(tblWineFields.REGION.toString())));
		else if(wine.getAppellation()!=null)
			wine.setRegion(wine.getAppellation().getTblRegions());
		else if(wine.getWinery()!=null)
			wine.setRegion(this.wineService.getRegion(wine.getWinery().getRegionId()));

		if(wineValues.containsKey(tblWineFields.COUNTRY.toString()))
			wine.setCountry(this.wineService.getCountry(wineValues.get(tblWineFields.COUNTRY.toString())));
		else if(wine.getRegion()!=null)
			wine.setCountry(wine.getRegion().getTblCountries());
		else if(wine.getAppellation()!=null)
			wine.setCountry(wine.getAppellation().getTblCountries());
		else if(wine.getWinery()!=null)
			wine.setCountry(wine.getWinery().getTblCountries());

		return wine;
    }

    private tblPartnersProducts setPartnerProductsValues()
    {
    	//TODO
    	return null;
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
}