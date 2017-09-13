package winedunk.pf.services;

import java.io.IOException;

import javax.ejb.LocalBean;
import javax.ejb.Stateful;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.models.tblPartnersProducts;

/**
 * Session Bean implementation class PartnersProductsService
 */
@Stateful
@LocalBean
public class PartnersProductsService {

	private final RequestsCreator requestsCreator;
    private final ObjectMapper mapper;
    private final String servletUrl;
    String apiUrl;

    public PartnersProductsService() {
    	this.requestsCreator = new RequestsCreator();
        this.mapper = new ObjectMapper();
        this.servletUrl = "partnersProductss";
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
     * @param product
     * @return
     * @throws NumberFormatException
     * @throws JsonProcessingException
     * @throws IOException
     */
    public Integer insertProduct(tblPartnersProducts product) throws NumberFormatException, JsonProcessingException, IOException
    {
    	String response = requestsCreator.createPostRequest(apiUrl, servletUrl+"?action=addPartnersProducts", mapper.writeValueAsString(product));
    	return response.isEmpty() ? null : Integer.valueOf(response);
    }

    /**
     * 
     * @param product
     * @return
     * @throws JsonProcessingException
     * @throws IOException
     */
    public Boolean updateProduct(tblPartnersProducts product) throws JsonProcessingException, IOException
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
    public tblPartnersProducts getInstance(String apiUrl, String partnerProductId, String merchantProductId) throws IOException
    {
    	this.setApiUrl(apiUrl);

    	//get possibly existing product
    	String requestParameters = "partnerProductId="+partnerProductId
 		 						 + "&merchanProductId="+merchantProductId;
    	
    	String productString = requestsCreator.createPostRequest(apiUrl, "partnersProductss?action=getByPartnerProductIdAndMerchantProductId", requestParameters);
    	
    	return this.mapper.readValue(productString, tblPartnersProducts.class);
	}
}
