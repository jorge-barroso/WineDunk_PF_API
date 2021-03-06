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
import winedunk.pf.models.TblPFLogProcesses;
import winedunk.pf.models.TblPFLogTypes;
import winedunk.pf.models.TblWinesGrapeVariety;
import winedunk.pf.models.TblWinesWineType;
import winedunk.pf.models.Tblpfmerchanthtmlparsing;
import winedunk.pf.models.Tblpfproduct;
import winedunk.pf.models.tblAppellations;
import winedunk.pf.models.tblClosures;
import winedunk.pf.models.tblColours;
import winedunk.pf.models.tblGrapeVarieties;
import winedunk.pf.models.tblPartners;
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
import winedunk.pf.services.PFLogService;
import winedunk.pf.services.PartnersProductsService;
import winedunk.pf.services.ProductService;
import winedunk.pf.services.RequestsCreator;
import winedunk.pf.services.WineService;
import winedunk.pf.services.XmlDataExtractor;

public class ProductsProcessRunnable implements Callable<Integer>{

	private final ObjectMapper mapper = new ObjectMapper();
	private final Properties properties;
	private Tblpfproduct product;
	private WineService wineService;
	private PartnersProductsService partnersProductsService;
	
	// sending in log types name, so it needs only 1 call the CRUD
	private String logTypeErrorName = "";
	private String logTypeWarningName = "";
	private String logTypeInformationName = "";
	
	// aripe, logs management
	TblPFLogProcesses pfLogProcesses = new TblPFLogProcesses();
	TblPFLogTypes pfLogType = new TblPFLogTypes();	
	private PFLogService pfLogService = new PFLogService();
	
	
	/**
	 * 
	 * @param product
	 * @param properties
	 * @param executionDate
	 */
	public ProductsProcessRunnable(Properties properties, Date executionDate, Tblpfproduct product, String logTypeErrorName, String logTypeWarningName, String logTypeInformationName)
	{
		this.properties = properties;
		this.product = product;
		this.mapper.setSerializationInclusion(Include.NON_NULL);
		
		this.logTypeErrorName = logTypeErrorName;
		this.logTypeWarningName = logTypeWarningName;
		this.logTypeInformationName = logTypeInformationName;
	}

	/**
	 * <p>Default call method that will run in background automatically and return the product feed id of this product, that will be stored in a list on the main asynchronous thread on @see winedunk.pf.controllers.ProductsProcessor#doGet()</p>
	 * <p>We will use that list later to count how many wines have been processed </p>
	 * 
	 */
	@Override
	public Integer call() {
		try {
			
			// aripe, logs management 
			pfLogService.ProductProcessingBegin(product.getTblpf().getPartnerId(), product.getPartnerProductId());
			
			// processing product
			Integer id = productProcess(product);
			
			// aripe, logs management 
			pfLogService.ProductProcessingEnd(product.getTblpf().getPartnerId(), product.getPartnerProductId());
						
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
    private Integer productProcess(Tblpfproduct product) {
    	// aripe 2018-04-10 whole process logic has been changed

		if ( (product != null) &&
			 (product.getTblpf().getPartnerId() != null) &&
			 (product.getTblpf().getPartnerId().getId() > 0) &&
			 (product.getPartnerProductId() != "") &&
			 (product.getClicktag() != "") &&
			 (product.getName() != "") &&
			 (product.getProductURL() != "") 
		   ) {
			
			// main mandatory information exists in PF so we get started
			
			this.wineService = new WineService(properties);
			this.partnersProductsService = new PartnersProductsService(properties.getProperty("crud.url"));
			
			// looking if current product exits in `tblPartnersProducts`
	    	tblPartnersProducts partnerProduct = new tblPartnersProducts();
			try {
				partnerProduct = this.partnersProductsService.getProduct(product.getTblpf().getPartnerId().getId(), product.getPartnerProductId());
				
				if ( (partnerProduct != null) && (partnerProduct.getId() != null) && (partnerProduct.getId() > 0) ) {
					
					// we already got this product
					// setting pfLogTypes as Information
					pfLogService.ProductProcessing(partnerProduct.getPartnerId(), logTypeInformationName, product.getPartnerProductId() , "tblPartnersProducts", partnerProduct.getId(), "Existing product found in `tblPartnersProducts` [`id`=" + partnerProduct.getId() + ", `wineId`=" + partnerProduct.getTblWines().getId() + ", `shopId`=" + partnerProduct.getShopId().getId() + "]");

    				// updating `tblWines`.`imageURL if null
					UpdateWineImageIfNull(partnerProduct.getTblWines(), product.getImageURL(), partnerProduct.getPartnerId(), product.getPartnerProductId() );
						
					// if either "partnerProduct.getLastUpdated() == null" or "partnerProduct.getLastUpdated()" is older than product.getTblpf().getLastStandardisation(), we have to process this product
		    		// checking if product price has to be updated
		    		if (product.getPrice() != null) {
		    		
		    			if (!partnerProduct.getPartnerProductPrice().equals(product.getPrice())) {
			    			// updating price
			    			if (partnersProductsService.updateProduct(partnerProduct.getId(), product.getPrice())) {
			    				pfLogService.ProductProcessing(partnerProduct.getPartnerId(), logTypeInformationName, product.getPartnerProductId() , "tblPartnersProducts", partnerProduct.getId(), "Product price updated: `tblPartnersProducts` | `id`=" + partnerProduct.getId() + ", old `partnerProductPrice`=" + partnerProduct.getPartnerProductPrice() + ", new price=" + product.getPrice() );
			    				return partnerProduct.getId();
			    			} else {
								pfLogService.ProductProcessing(partnerProduct.getPartnerId(), logTypeErrorName, product.getPartnerProductId() , "tblPartnersProducts", partnerProduct.getId(), "Product price not updated in existing product in `tblPartnersProducts` | `id`=" + partnerProduct.getId() + ", `partnerProductPrice`=" + partnerProduct.getPartnerProductPrice() + ", old `partnerProductPrice`=" + partnerProduct.getPartnerProductPrice() + ", new price=" + product.getPrice() );
								return null;
			    			}
			    		} else {
			    			// price in PF is the same as the one in `tblPartnersProducts`.`partnerProductPrice`, so there is no change, nothing to do here
			    			pfLogService.ProductProcessing(partnerProduct.getPartnerId(), logTypeInformationName, product.getPartnerProductId() , "tblPartnersProducts", partnerProduct.getId(), "Product price not updated in `tblPartnersProducts` because PF price and partnerProductPrice are the same [`id`=" + partnerProduct.getId() + ", `partnerProductPrice`=" + partnerProduct.getPartnerProductPrice() + ", PF price=" + product.getPrice() + "]");
			    			return null;
			    		}
		    			
		    		} else {
		    			// the price that comes into PF is null, so nothing to do here, just report it
		    			pfLogService.ProductProcessing(partnerProduct.getPartnerId(), logTypeWarningName, product.getPartnerProductId() , "", 0, "Product price not updated because PF product price = null: PF partnerId=" + product.getTblpf().getPartnerId().getId() + ", PF partnerproductId=" + product.getPartnerProductId() );
		    			return null;
		    		}

				} else {
					
					// NEW PRODUCT

					pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeInformationName, product.getPartnerProductId() , "tblPartnersProducts", 0, "Could not find an existing product by `partnerId`=" + product.getTblpf().getPartnerId().getId() + " and `partnerProductId`=" + product.getPartnerProductId() + " - processing it as a potential NEW product");

					// getting partnersMerchant based on partnerMerchantName to be used later on
    				tblPartnersMerchants partnersMerchant = this.getMerchantBypartnerMerchantName(product.getMerchantName(), product.getTblpf().getPartnerId(), product.getPartnerProductId() );
					
					//Get wine values by parsing the website
					
			    	// Getting  Merchant parsing from `tblPFMerchantHTMLParsing`
					Map<String, String> wineValues=null;
					List<Tblpfmerchanthtmlparsing> merchantParsing = this.getParsingInstructions(product.getMerchantName(), product.getTblpf().getPartnerId(), product.getPartnerProductId());

					if(!merchantParsing.isEmpty()) {
					
						try {
							wineValues = this.getWineValues(product, merchantParsing, product.getTblpf().getPartnerId(), product.getPartnerProductId());
						} finally {
							if(wineValues==null) 
								wineValues = new HashMap<String, String>();
						}
						
					} else {

						pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeWarningName, product.getPartnerProductId(), "", 0, "No parsing found for merchant " + product.getMerchantName() + " - new product [partnerId="+product.getTblpf().getPartnerId().getId()+", partnerProductId="+product.getPartnerProductId()+"] could not be added");
						return null; 
					}
					
					// extracting wine colour from wine type
					if(!wineValues.containsKey(TblWineFields.COLOUR))
					{
						if(StringUtils.containsIgnoreCase(product.getProductType(), Colours.RED))
							wineValues.put(TblWineFields.COLOUR, Colours.RED);
						else if(StringUtils.containsIgnoreCase(product.getProductType(), Colours.WILDCARD_ROSE))
							wineValues.put(TblWineFields.COLOUR, Colours.ROSE);
						else if(StringUtils.containsIgnoreCase(product.getProductType(), Colours.WHITE))
							wineValues.put(TblWineFields.COLOUR, Colours.WHITE);
					}
					
					// creating new wine instance
					tblWines wine = new tblWines();
					
					//setting wine values
			    	wine = this.setWineValues(wine, wineValues, product.getName(), product.getPartnerProductDescription(), product.getTblpf().getPartnerId(), product.getPartnerProductId());
			    	
			    	if(Thread.currentThread().isInterrupted())
			    	{
			    		pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeErrorName, product.getPartnerProductId(), "", 0, "An exception caused this thread to be interrupted while generating wine: [partnerId = " + product.getTblpf().getPartnerId().getId() + ", partnerProductId = " + product.getPartnerProductId() + ", productname = " + product.getName() + "]" );
			    		return null;
			    	}
			    	
			    	// Sanitise the name removing unwanted details and extract those details as possible values for other fields in the tblWines table
					try {
						wine = this.wineService.completeDataFromName(wine, product.getMerchantName());
					} catch(Exception e) {
						e.printStackTrace();
						return null;
					}
					
					// looking if new wine already exists in `tblWines` based on (GTIN) or (Name + bottle size + vintage)
					
					tblWines existingWine;
					
					if ( (wineValues.get(TblWineFields.GTIN) != null) || 
						 ( (wine.getName() != null) && (wine.getName() != "") ) ) {

						// otherwise it doesn't make any sense (`name` can't be null or empty)

						pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeInformationName, product.getPartnerProductId(), "tblWines", 0, "Looking if potential new wine exists in `tblWines` based on (`gtin`=\"" + wineValues.get(TblWineFields.GTIN) + "\") OR (`name`=\"" + wine.getName() + "\" and `bottleSize`=\"" + wineValues.get(TblWineFields.BOTTLE_SIZE) + "\" and `vintage`=\"" + wineValues.get(TblWineFields.VINTAGE) + "\") " );
				    	try {
				    		existingWine = this.wineService.getInstance(wine.getName(),
				    													wineValues.get(TblWineFields.GTIN),
																		wineValues.get(TblWineFields.BOTTLE_SIZE),
																		wineValues.get(TblWineFields.VINTAGE));
						} catch (JsonParseException e4) {
							pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeErrorName, product.getPartnerProductId(), "", 0, "While trying to find the possibly existing wine, the JSON response from the CRUD doesn't seem to have a valid format" );
							e4.printStackTrace();
							existingWine = null;
						} catch (JsonMappingException e4) {
							pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeErrorName, product.getPartnerProductId(), "", 0, "While trying to find the possibly existing wine, the JSON response from the CRUD doesn't seem to have a valid format" );
							e4.printStackTrace();
							existingWine = null;
						} catch (IOException e4) {
							pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeErrorName, product.getPartnerProductId(), "", 0, "While trying to find the possibly existing wine, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status" );
							e4.printStackTrace();
							existingWine = null;
						}
				    	
					} else {
						existingWine = null;
					}
						
					if ( (existingWine != null) && (existingWine.getId() != null) && (existingWine.getId() > 0) ) {
			    		// existing wine found!
			    		
						// using existing wine for inserting into `tblPartnersProducts`
			    		wine = existingWine;
			    		
			    		// updating `tblWines`.`imageURL if null
						UpdateWineImageIfNull(wine, product.getImageURL(), product.getTblpf().getPartnerId(), product.getPartnerProductId());

						pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeInformationName, product.getPartnerProductId(), "tblWines", wine.getId(), "An existing wine (`tblWines`.`id`=" + wine.getId() + ") has been found in `tblWines` based on (`gtin`=" + wineValues.get(TblWineFields.GTIN) + ") OR (`name`=" + wine.getName() + " and `bottleSize`=" + wineValues.get(TblWineFields.BOTTLE_SIZE) + " and `vintage`=" + wineValues.get(TblWineFields.VINTAGE) + ") ");
			    		
	    				partnerProduct = new tblPartnersProducts();
	    				partnerProduct.setPartnerId(product.getTblpf().getPartnerId());
	    				partnerProduct.setTblWines(wine);
	    				partnerProduct.setShopId(partnersMerchant.getShop());
	    				partnerProduct.setPartnerProductId(product.getPartnerProductId());
	    				partnerProduct.setPartnerProductPrice(product.getPrice());
	    				partnerProduct.setPartnerDestinationUrl(product.getProductURL());
	    				partnerProduct.setPartnerMerchantId(product.getPartnerMerchantId());
	    				partnerProduct.setPartnerMerchantProductId(product.getMerchantProductId());
	    				partnerProduct.setPartnerMerchantStock(null);
	    				partnerProduct.setPartnerMerchantDeliveringCost(product.getDeliveryCost());
	    				
	    				// flagging partnerProduct related to new wine as deleted = yes, so it won't be displayed until manually reviewed
	    				partnerProduct.setDeleted(true);
	    				
	    				Integer newPartnerProductId = this.partnersProductsService.insertProduct(partnerProduct);
	    				if ( (newPartnerProductId != null) && (newPartnerProductId > 0) ) {
	    					// all done!

							pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeInformationName, product.getPartnerProductId(), "tblPartnersProducts", newPartnerProductId, "A new record has been added to `tblPartnersProducts` [`id`=" + newPartnerProductId + "]" );
	    					return newPartnerProductId;
	    				} else {

	    					pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeWarningName, product.getPartnerProductId(), "tblPartnersProducts", 0, "Atention! New wine: [wineId=" + wine.getId() + "] has been added to `tblWines` however it was NOT possible to add the registre into `tblPartnersProducts` [partnerId=" + product.getTblpf().getPartnerId() + ", partnerProductId=" + product.getPartnerProductId() + "]");
		    				return null;
	    				}
	    				
			    	} else {	
			    		// no existing wine was found, we have to get it inserted as a new wine
			    		
			    		pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeWarningName, product.getPartnerProductId(), "", 0, "No existing wine was found in `tblWines`, considering it as a NEW wine");
			    		if (wine.getId() == null) {
							try {
								// flagging new wine as deleted = yes, so it won't be displayed until manually reviewed
								wine.setDeleted(true);
								// inserting new wine
								wine.setId(this.wineService.insertWine(wine));
							} catch (NumberFormatException e) {
								pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeErrorName, product.getPartnerProductId(), "", 0, "Id returned by the CRUD while inserting the wine was not a proper number");
								e.printStackTrace();
								return null;
							} catch (JsonProcessingException e) {
								pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeErrorName, product.getPartnerProductId(), "", 0, "An exception came up while serialising the wine to JSON before sending it for insertion in the database");
								e.printStackTrace();
								return null;
							} catch (IOException e) {
								pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeErrorName, product.getPartnerProductId(), "", 0, "While sending the wine to the CRUD for insertion, the CRUD was not reachable");
								e.printStackTrace();
								return null;
							}
						} else {
							// something very weird has happened (a new wine had an Id)
							pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeWarningName, product.getPartnerProductId(), "tblWine", wine.getId(), "Logical ERROR: a wine supposed to be new had an Id in it: [partnerId=" + product.getTblpf().getPartnerId().getId() + ", partnerProductId=" + product.getPartnerProductId() + ", wineId=" + wine.getId() + "]");
							return null;
						}
			    		
			    		if (wine.getId() > 0) {
			    			// new wine has been added
			    			pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeInformationName, product.getPartnerProductId(), "tblWines", wine.getId(), "New wine added into `tblWines` [`id`=" + wine.getId() + "]");
			    			
			    			// updating `tblWines`.`imageURL if null
							UpdateWineImageIfNull(wine, product.getImageURL(), product.getTblpf().getPartnerId(), product.getPartnerProductId());
							
			    			// setting grape varieties
			    			if(!StringUtils.isBlank(wineValues.get(TblWineFields.WINE_GRAPEVARIETY)))
			    	    	{	
			    				String[] grapeVarieties = wineValues.get(TblWineFields.WINE_GRAPEVARIETY).split(",|-|\\s+and\\s+");
			    	        	for (String grapeVariety : grapeVarieties)
			    	        	{
			    	        		grapeVariety = grapeVariety.trim();
			    	        		if (!StringUtils.isBlank(grapeVariety)) {
			    	        			wine = this.setWinesGrapeVarieties(grapeVariety, wine, product.getTblpf().getPartnerId(), product.getPartnerProductId());
			    	        			pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeInformationName, product.getPartnerProductId(), "tblWinesGrapeVarieties", 0, "Grape varieties have been linked to the wine [`wineId`=" + wine.getId() + ", grapeVariety name = " + grapeVariety + "]" );
			    	        		}
			    	        	}
			    	    	}
			    			
			    			// setting wine types
			    			if(!StringUtils.isBlank(wineValues.get(TblWineFields.WINE_TYPE)) && wineValues.get(TblWineFields.WINE_TYPE).length()<=100) {
			    				this.setWinesWineType(wine, wineValues.get(TblWineFields.WINE_TYPE), product.getTblpf().getPartnerId(), product.getPartnerProductId());
			    				pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeInformationName, product.getPartnerProductId(), "tblWinesWineTypes", 0, "Wine types linked to the wine [`wineId`=" + wine.getId() + ", winetypes = " + wineValues.get(TblWineFields.WINE_TYPE) + "]");
			    			} else if(!StringUtils.isBlank(product.getProductType())) {
			    				this.setWinesWineType(wine, product.getProductType(), product.getTblpf().getPartnerId(), product.getPartnerProductId());
			    				pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeInformationName, product.getPartnerProductId(), "tblWinesWineTypes", 0, "Wine types linked to the wine | `wineId`=" + wine.getId() + ", winetypes = " + product.getProductType() + "]");
			    			} else {
			    				this.setWinesWineType(wine, NoDataFieldsValues.NO_WINETYPE, product.getTblpf().getPartnerId(), product.getPartnerProductId());
			    				pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeWarningName, product.getPartnerProductId(), "tblWinesWineTypes", 0, "No wine types have been linked to the new wine");
			    			}
			    			
			    			// updating wine after setting imageURL + grape varieties + wine types
			    			if (this.wineService.updateWine(wine)) {
			    				// new wine updated
			    				pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeInformationName, product.getPartnerProductId(), "tblWines", wine.getId(), "ImageURL, Grape varieties and Wine types have been updated for wine [wineId=" + wine.getId() + "]");

			    				
			    				// taking care of `tblPartnersProducts`
			    				partnerProduct = new tblPartnersProducts();
			    				partnerProduct.setPartnerId(product.getTblpf().getPartnerId());
			    				partnerProduct.setTblWines(wine);
			    				partnerProduct.setShopId(partnersMerchant.getShop());
			    				partnerProduct.setPartnerProductId(product.getPartnerProductId());
			    				partnerProduct.setPartnerProductPrice(product.getPrice());
			    				partnerProduct.setPartnerDestinationUrl(product.getProductURL());
			    				partnerProduct.setPartnerMerchantId(product.getPartnerMerchantId());
			    				partnerProduct.setPartnerMerchantProductId(product.getMerchantProductId());
			    				partnerProduct.setPartnerMerchantStock(null);
			    				partnerProduct.setPartnerMerchantDeliveringCost(product.getDeliveryCost());
			    				
			    				// flagging partnerProduct related to new wine as deleted = yes, so it won't be displayed until manually reviewed
			    				partnerProduct.setDeleted(true);
			    				
			    				Integer newPartnerProductId = this.partnersProductsService.insertProduct(partnerProduct);
			    				if ( (newPartnerProductId != null) && (newPartnerProductId > 0) ) {
			    					// all done!

			    					pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeInformationName, product.getPartnerProductId(), "tblPartnersProducts", newPartnerProductId, "New record has been added to `tblPartnersProducts` [`id`=" + newPartnerProductId + "]");
			    					return newPartnerProductId;
			    				} else {

			    					pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeWarningName, product.getPartnerProductId(), "tblPartnersProducts", 0, "New wine: [wineId=" + wine.getId() + "] has been added to `tblWines` but was not possible to add the record into `tblPartnersProducts`, partnerProduct=\"" + partnerProduct + "\"");
				    				return null;
			    				}
			    				
			    			} else {
			    				// new wine couldn't be updated

			    				pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeWarningName, product.getPartnerProductId(), "tblWines", wine.getId(), "New wine: [wineId=" + wine.getId() + "] has been added to `tblWines` but ImageURL=\"" + product.getImageURL() + "\", grape varieties=" + wineValues.get(TblWineFields.WINE_GRAPEVARIETY) + " and wineTypes=" + wineValues.get(TblWineFields.WINE_TYPE) + " have not been updated");
			    				return null;
			    			}
			    			
			    		} else {
			    			pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeErrorName, product.getPartnerProductId(), "tblWines", 0, "An error ocurred while inserting new wine [partnerId=" + product.getTblpf().getPartnerId().getId() + ", partnerProductId=" + product.getPartnerProductId() + ", wineId=" + wine.getId() + "]");
							return null;
			    		}
			    		
			    	} 
					
				} // End new product

			} catch (Exception e) {
				pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeErrorName, product.getPartnerProductId(), "", 0, "Exception at winedunk.pf.runnables/productProcess(Tblpfproduct product)");
				System.out.println("Exception at winedunk.pf.runnables/productProcess(Tblpfproduct product) - StackTrace:");
				e.printStackTrace();
				return null;
			}
			
		} else {
			// missing mandatory information, so we do not process this product at all
			
			pfLogService.ProductProcessing(product.getTblpf().getPartnerId(), logTypeWarningName, product.getPartnerProductId(), "", 0, "Mandatory information is missing, product = [" + product + "] has NOT been processed");
			return null;
		}
		
    }

    private void UpdateWineImageIfNull(tblWines wine, String pfImageURL, tblPartners partner, String partnerProduct) {
    	// updating `tblWines`.`imageURL if null
		if ( (wine != null) && (wine.getId() != null) && (pfImageURL != null) && ( (wine.getImageURL() == null) || (wine.getImageURL() == "") ) ){
			String finalImageName;
			try {
				finalImageName = this.wineService.getImageName(pfImageURL, wine.getId());
				wineService.getImage(finalImageName, pfImageURL);
				String wineImageURL = properties.getProperty("images.host.url")+"/"+finalImageName;
				wine.setImageURL(wineImageURL);

				if (this.wineService.updateWine(wine)) {
					pfLogService.ProductProcessing(partner, logTypeInformationName, partnerProduct, "tblWines", wine.getId(), "Wine Image updated [wineId=" + wine.getId() + ", imageURL=" + wineImageURL + "]");
				} else {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "tblWines", wine.getId(), "Wine Image could NOT be updated [wineId=" + wine.getId() + ", imageURL=" + wineImageURL + "]");
				}
			} catch (Exception e) {
				e.printStackTrace();
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
    private List<Tblpfmerchanthtmlparsing> getParsingInstructions(String pfMerchantName, tblPartners partner, String partnerProduct)
    {
    	try {
    		// aripe 2018-04-05
			//tblShops merchant = this.getMerchant(merchantName);
    		tblPartnersMerchants partnersMerchants = this.getMerchantBypartnerMerchantName(pfMerchantName, partner, partnerProduct);
    		tblShops merchant = partnersMerchants.getShop();
    		
			if(merchant.getId()==null)
			{
				System.out.println("Exception: Couldn't find merchant, so no parsing data can be provided");
				return new ArrayList<Tblpfmerchanthtmlparsing>();
			}

			//get parsing instructions by the merchant
			String merchantParsingString;
			try {
				merchantParsingString = RequestsCreator.createPostRequest(this.properties.getProperty("crud.url"), "TblPfMerchantsHTMLParsing?action=getByMerchant" , "{ \"id\" : "+merchant.getId()+" }", null);
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Exception: Couldn't reach CRUD destination while attempting to get the list of parsing instructions");
				return new ArrayList<Tblpfmerchanthtmlparsing>();
			}
			
			if(StringUtils.isBlank(merchantParsingString))
			{
				System.out.println("Exception: Could find the merchant, but not the mapping! Skipping");
				return new ArrayList<Tblpfmerchanthtmlparsing>();
			}
	
			try {
				return this.mapper.readValue(merchantParsingString, new TypeReference<List<Tblpfmerchanthtmlparsing>>(){});
			} catch (JsonParseException e) {
				System.out.println("Exception: While trying to get the merchant parsing instructions, the JSON response by the CRUD doesn't seem to have a valid format");
				e.printStackTrace();
			} catch (JsonMappingException e) {
				System.out.println("Exception: While trying to get the merchant parsing instructions, the JSON response by the CRUD couldn't be mapped with a valid Tblpfmerchanthtmlparsing object");
				e.printStackTrace();
			} catch (IOException e) {
				System.out.println("Exception: While trying to get the merchant parsing instructions, a low level I/O exception occurred");
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
    private Map<String, String> getWineValues(Tblpfproduct product, List<Tblpfmerchanthtmlparsing> merchantParsings, tblPartners partner, String partnerProduct) throws Exception
    {
		// aripe 2018-04-05
		// DataSource dataSource = this.getMerchant(product.getMerchantName()).getDataSource();
    	
    	tblPartnersMerchants partnersMerchants = this.getMerchantBypartnerMerchantName(product.getMerchantName(), partner, partnerProduct);
    	DataSource dataSource = partnersMerchants.getShop().getDataSource();

    	ProductService productService = new ProductService();

    	DataExtractor dataExtractor;
    	switch(dataSource.getContentType())
    	{
	    	case HTML:
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
    private tblWines setWineValues(tblWines wine, Map<String, String> wineValues, String name, String description, tblPartners partner, String partnerProduct)
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
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the closure by its name from the CRUD, the response doesn't seem to have a valid JSON format");
				e1.printStackTrace();
				return null;
			} catch (JsonMappingException e1) {
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the closure by its name from the CRUD, the JSON response couldn't be mapped to a tblClosure object (discrepancies between model and JSON)");
				e1.printStackTrace();
				return null;
			} catch (IOException e1) {
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the closure by its name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
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
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the colour by its name from the CRUD, the response doesn't seem to have a valid JSON format");
				e1.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (JsonMappingException e1) {
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the colour by its name from the CRUD, the JSON response couldn't be mapped to a tblClosure object (discrepancies between model and JSON)");
				e1.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (IOException e1) {
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the colour by its name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
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
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the winery by its name from the CRUD, the response doesn't seem to have a valid JSON format");
				e1.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (JsonMappingException e1) {
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the winery by its name from the CRUD, the JSON response couldn't be mapped to a tblWineries object (discrepancies between model and JSON)");
				e1.printStackTrace();
				Thread.currentThread().interrupt();
				return null;
			} catch (IOException e1) {
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the winery by its name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
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
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the appellation by its name, the JSON response from the CRUD doesn't seem to have a valid format");
					e.printStackTrace();
					Thread.currentThread().interrupt();
					return null;
				} catch (JsonMappingException e) {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the appellation by its name, the JSON response from the CRUD doesn't seem to have a valid format");
					e.printStackTrace();
					Thread.currentThread().interrupt();
					return null;
				} catch (IOException e) {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the appellation by its name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
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
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the appellation by its id taken from the winery, the JSON response from the CRUD doesn't seem to have a valid format");
					e.printStackTrace();
					Thread.currentThread().interrupt();
					return null;
				} catch (JsonMappingException e) {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the appellation by its id taken from the winery, the JSON response from the CRUD doesn't seem to have a valid format");
					e.printStackTrace();
					Thread.currentThread().interrupt();
					return null;
				} catch (IOException e) {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the appellation by its id taken from the winery, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
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
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the default 'No Appellation' appellation, the JSON response from the CRUD doesn't seem to have a valid format");
					e.printStackTrace();
					Thread.currentThread().interrupt();
					return null;
				} catch (JsonMappingException e) {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the default 'No Appellation' appellation, the JSON response from the CRUD doesn't seem to have a valid format");
					e.printStackTrace();
					Thread.currentThread().interrupt();
					return null;
				} catch (IOException e) {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the default 'No appellation' appellation, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
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
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the region by its name, the JSON response from the CRUD doesn't seem to have a valid format");
					e.printStackTrace();
					Thread.currentThread().interrupt();
					return null;
				} catch (JsonMappingException e) {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the region by its name, the JSON response from the CRUD doesn't seem to have a valid format");
					e.printStackTrace();
					Thread.currentThread().interrupt();
					return null;
				} catch (IOException e) {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the region by its name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
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
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the region by its id taken from the winery, the JSON response from the CRUD doesn't seem to have a valid format");
					e.printStackTrace();
					Thread.currentThread().interrupt();
					return null;
				} catch (JsonMappingException e) {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the region by its id taken from the winery, the JSON response from the CRUD doesn't seem to have a valid format");
					e.printStackTrace();
					Thread.currentThread().interrupt();
					return null;
				} catch (IOException e) {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the region by its id taken from the winery, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
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
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the default 'No Region' region, the JSON response from the CRUD doesn't seem to have a valid format");
					e.printStackTrace();
					Thread.currentThread().interrupt();
					return null;
				} catch (JsonMappingException e) {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the default 'No Region' region, the JSON response from the CRUD doesn't seem to have a valid format");
					e.printStackTrace();
					Thread.currentThread().interrupt();
					return null;
				} catch (IOException e) {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the default 'No Region' region, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
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
				wine.getCountry().setDeleted(true);
				wine.getCountry().setId(Integer.parseInt(RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "countries?action=addCountry", this.mapper.writeValueAsString(wine.getCountry()), null)));
			}

			//Add country to the region if necessary and then insert or update it 
			if(region.getTblCountries()==null)
			{
				region.setTblCountries(wine.getCountry());
				
				if(region.getId()==null)
				{
					try {
						region.setId(Integer.valueOf(RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "regions?action=addRegion", this.mapper.writeValueAsString(region), null)));
					} catch (JsonProcessingException e) {
						pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While sending a request to the CRUD to insert the region, something went wrong serialising it to JSON");
						e.printStackTrace();
						Thread.currentThread().interrupt();
						return null;
					} catch (IOException e) {
						pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "Couldn't reach the crud while sending a request to insert the region");
						e.printStackTrace();
						Thread.currentThread().interrupt();
						return null;
					}
				}
				else
				{
					try {
						RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "regions?action=updateRegion", this.mapper.writeValueAsString(region), null);
					} catch (JsonProcessingException e) {
						pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While sending a request to the CRUD to update the region, something went wrong serialising it to JSON");
						e.printStackTrace();
						Thread.currentThread().interrupt();
						return null;
					} catch (IOException e) {
						pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "Couldn't reach the crud while sending a request to update the region");
						e.printStackTrace();
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
						pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While sending a request to the CRUD to insert the appellation, something went wrong serialising it to JSON");
						e.printStackTrace();
						Thread.currentThread().interrupt();
						return null;
					} catch (IOException e) {
						pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "Couldn't reach the crud while sending a request to insert the appellation");
						e.printStackTrace();
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
						pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While sending a request to the CRUD to update the appellation, something went wrong serialising it to JSON");
						e.printStackTrace();
						Thread.currentThread().interrupt();
						return null;
					} catch (IOException e) {
						pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "Couldn't reach the crud while sending a request to update the appellation");
						e.printStackTrace();
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
						id = RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "wineries?action=addWinery", this.mapper.writeValueAsString(winery), null);
					} catch (JsonProcessingException e) {
						pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While sending a request to the CRUD to insert the winery, something went wrong serialising it to JSON");
						e.printStackTrace();
						Thread.currentThread().interrupt();
						return null;
					} catch (IOException e) {
						pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "Couldn't reach the crud while sending a request to insert the winery");
						e.printStackTrace();
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
						pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While sending a request to the CRUD to update the winery, something went wrong serialising it to JSON");
						e.printStackTrace();
						Thread.currentThread().interrupt();
						return null;
					} catch (IOException e) {
						pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "Couldn't reach the crud while sending a request to update the winery");						
						e.printStackTrace();
						Thread.currentThread().interrupt();
						return null;
					}
				}
			}
	
			wine.setWinery(winery);
    		
			pfLogService.ProductProcessing(partner, logTypeInformationName, partnerProduct, "tblWines", wine.getId(), "wines values set for wineId = " + wine.getId());
	
			return wine;
    	} catch (Exception e) {
    		e.printStackTrace();
    		return null;
    	}
    }

    /**
     * 
     * @param wine
     * @param wineTypeName
     * @throws JsonParseException
     * @throws JsonMappingException
     * @throws IOException
     */
    public void setWinesWineType(tblWines wine, String wineTypeName, tblPartners partner, String partnerProduct)
	{
    	wineTypeName = WordUtils.capitalizeFully(StringUtils.stripAccents(wineTypeName));
    	String encodedWineTypeName;
    	try {
			encodedWineTypeName = URLEncoder.encode(wineTypeName, "UTF-8");
		} catch (UnsupportedEncodingException e2) {
			pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "Error while encoding wineTypeName using UTF-8");
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
			pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the possibly existing wine type by its name from the CRUD, the response doesn't seem to have a valid JSON format");
			e1.printStackTrace();
			return;
		} catch (JsonMappingException e1) {
			pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the possibly existing wine type by its name from the CRUD, the JSON response couldn't be mapped to a tblWineType object (discrepancies between model and JSON)");
			e1.printStackTrace();
			return;
		} catch (IOException e1) {
			pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the possibly existing wine type by its name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
			e1.printStackTrace();
			return;
		}

		//if it didn't exist, insert, otherwise, check if the whole relationship exists
		TblWinesWineType winesWineType;
		if(wineType.getId()==null)
		{
			wineType.setName(wineTypeName);
			wineType.setDeleted(false);
			String response = null;
			try {
				response = RequestsCreator.createPostRequest(this.properties.getProperty("crud.url"), "winetypes?action=addWineType", this.mapper.writeValueAsString(wineType), null);
				wineType.setId(Integer.parseInt(response));
			} catch (NumberFormatException e) {
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "Id returned by the CRUD while inserting the wine type was not a proper number: "+response);
				e.printStackTrace();
				return;
			} catch (JsonProcessingException e) {
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "An exception came up while serialising the wine type to JSON before sending it for insertion in the database");
				e.printStackTrace();
				return;
			} catch (IOException e) {
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While sending the wine type to the CRUD for insertion, the CRUD was not reachable");
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
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the possibly existing tblWinesWineType by wine id and wine type id from the CRUD, the response doesn't seem to have a valid JSON format");
				e1.printStackTrace();
				return;
			} catch (JsonMappingException e1) {
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the possibly existing tblWinesWineType  by wine id and wine type id from the CRUD, the JSON response couldn't be mapped to a tblClosure object (discrepancies between model and JSON)");
				e1.printStackTrace();
				return;
			} catch (IOException e1) {
				pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the possibly existing tblWinesWineType  by wine id and wine type id, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
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
			pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "An exception came up while serialising the tblWinesWineType to JSON before sending it for insertion in the database");
			e.printStackTrace();
			return;
		} catch (IOException e) {
			pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While sending the tblWinesWineType to the CRUD for insertion, the CRUD was not reachable");
			e.printStackTrace();
			return;
		}
	}

    public tblWines setWinesGrapeVarieties(String grapeVarietyName, tblWines wine, tblPartners partner, String partnerProduct)
    {
		grapeVarietyName = WordUtils.capitalizeFully(StringUtils.stripAccents(grapeVarietyName));

    	String grpVarietyJson;
		try {
			grpVarietyJson = RequestsCreator.createGetRequest(properties.getProperty("crud.url"), "grapevarieties?action=getByName&name="+URLEncoder.encode(grapeVarietyName, "UTF-8"), null);
		} catch (IOException e) {
			pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get a grape varieties by name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
			e.printStackTrace();
			return wine;
		}

		tblGrapeVarieties grapeVariety;
		try {
			grapeVariety = this.mapper.readValue(grpVarietyJson, tblGrapeVarieties.class);
		} catch (JsonParseException e) {
			pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get a grape varieties by name from the CRUD, the response doesn't seem to have a valid JSON format");
			// TODO Auto-generated catch block
			e.printStackTrace();
			return wine;
		} catch (JsonMappingException e) {
			pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get a grape varieties by name from the CRUD, the JSON response couldn't be mapped to a tblClosure object (discrepancies between model and JSON)");
			// TODO Auto-generated catch block
			e.printStackTrace();
			return wine;
		} catch (IOException e) {
			pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get a grape varieties by name, the CRUD couldn't be reached or a low level I/O exception arised mapping the response, check the server status");
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
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "Id returned by the CRUD while inserting new grapevarieties was not a proper number");
					// TODO Auto-generated catch block
					e.printStackTrace();
					return wine;
				} catch (JsonProcessingException e) {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "JsonProcessingException while inserting new grapevarieties");
					// TODO Auto-generated catch block
					e.printStackTrace();
					return wine;
				} catch (IOException e) {
					pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "IOException while inserting new grapevarieties");
					// TODO Auto-generated catch block
					e.printStackTrace();
					return wine;
				}
		}

		TblWinesGrapeVariety wineGrapeVariety = new TblWinesGrapeVariety();
		wineGrapeVariety.setGrapeVariety(grapeVariety);
		wineGrapeVariety.setWine(wine);

		wine.addTblWinesGrapeVariety(wineGrapeVariety);

		return wine;
    }

    
    private tblPartnersMerchants getMerchantBypartnerMerchantName(String partnerMerchantName, tblPartners partner, String partnerProduct)
    {
    	String merchantJson;
		try {
			
			merchantJson = RequestsCreator.createGetRequest(properties.getProperty("crud.url"), "partnersMerchants?action=getPartnersMerchantsBypartnerMerchantName&partnerMerchantName="+URLEncoder.encode(partnerMerchantName, "UTF-8"), null);
			
		} catch (IOException e) {
			pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "Couldn't reach the CRUD while trying to get \"getMerchantBypartnerMerchantName(" + partnerMerchantName + ")\"");
			e.printStackTrace();
			return new tblPartnersMerchants();
		}

		//parse json to an object and return it
		try {
			return this.mapper.readValue(merchantJson, tblPartnersMerchants.class);
		} catch (JsonParseException e) {
			pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the MerchantBypartnerMerchantName, the JSON response by the CRUD doesn't seem to have a valid format");
			e.printStackTrace();
		} catch (JsonMappingException e) {
			pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the MerchantBypartnerMerchantName, the JSON response by the CRUD couldn't be mapped with a valid tblShops object");
			e.printStackTrace();
		} catch (IOException e) {
			pfLogService.ProductProcessing(partner, logTypeErrorName, partnerProduct, "", 0, "While trying to get the MerchantBypartnerMerchantName, a low level I/O exception occurred");
			e.printStackTrace();
		}
		return new tblPartnersMerchants();
    }
    
}