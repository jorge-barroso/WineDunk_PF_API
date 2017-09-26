package winedunk.pf.services;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.models.tblPartnersProducts;

public class PartnersProductsService {

	private final RequestsCreator requestsCreator;
    private final ObjectMapper mapper;
    private final String servletUrl;
    String apiUrl;

    public PartnersProductsService(String apiUrl) {
    	this.requestsCreator = new RequestsCreator();
        this.mapper = new ObjectMapper();
        this.servletUrl = "partnersProductss";
        this.apiUrl = apiUrl;

        this.mapper.setSerializationInclusion(Include.NON_NULL);
    }

    /**
     * 
     * @param product
     * @return
     * @throws NumberFormatException
     * @throws JsonProcessingException
     * @throws IOException
     */
    public synchronized Integer insertProduct(tblPartnersProducts product) throws NumberFormatException, JsonProcessingException, IOException
    {
    	String response = this.requestsCreator.createPostRequest(apiUrl, servletUrl+"?action=addPartnersProducts", this.mapper.writeValueAsString(product));
    	System.out.println("ID: "+response);
    	return Integer.parseInt(response);
    }

    /**
     * 
     * @param product
     * @return
     * @throws JsonProcessingException
     * @throws IOException
     */
    public synchronized Boolean updateProduct(tblPartnersProducts product) throws JsonProcessingException, IOException
    {
    	String response = requestsCreator.createPostRequest(apiUrl, servletUrl+"?action=updatePartnersProducts", mapper.writeValueAsString(product));
    	return Boolean.valueOf(response);
    }

    /**
     * 
     * @param partnerProductId
     * @param merchantProductId
     * @return
     * @throws InterruptedException
     * @throws IOException 
     */
    public tblPartnersProducts getProduct(String partnerProductId, String merchantProductId) throws IOException
    {
    	//get possibly existing product
    	String requestParameters = "{ \"partnerProductId\" : "+partnerProductId+", "
 		 						 + "\"merchanProductId\" : "+merchantProductId+" }";
    	
    	String productString = requestsCreator.createPostRequest(apiUrl, "partnersProductss?action=getByPartnerProductIdAndMerchantProductId", requestParameters);
    	
    	if(productString.isEmpty())
    		return new tblPartnersProducts();

    	return this.mapper.readValue(productString, tblPartnersProducts.class);
	}
}
