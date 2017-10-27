package winedunk.pf.services;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.models.tblPartnersProducts;

public class PartnersProductsService {

	private final RequestsCreator requestsCreator;
    private final ObjectMapper mapper;
    private final String servletUrl;
    String apiUrl;

    /**
     * 
     */
    public PartnersProductsService(String apiUrl) {
    	this.requestsCreator = new RequestsCreator();
        this.mapper = new ObjectMapper();
        this.servletUrl = "partnersProductss";
        this.apiUrl = apiUrl;

        this.mapper.setSerializationInclusion(Include.NON_NULL);
    }


    /**
     * 
     * @return
     * @throws JsonParseException
     * @throws JsonMappingException
     * @throws IOException
     */
    public List<tblPartnersProducts> getAll() throws JsonParseException, JsonMappingException, IOException {
    	return this.mapper.readValue(this.requestsCreator.createGetRequest(apiUrl, servletUrl+"?action=getPartnersProductss"), new TypeReference<List<tblPartnersProducts>>(){});
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
    	String response = this.requestsCreator.createPostRequest(apiUrl, servletUrl+"?action=addPartnersProducts", this.mapper.writeValueAsString(product));
    	return Integer.parseInt(response);
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
    public tblPartnersProducts getProduct(String partnerProductId, String merchantProductId) throws IOException
    {
    	//get possibly existing product
    	String requestParameters = "action=getByPartnerProductIdAndMerchantProductId"
    							 + "&partnerProductId="+partnerProductId
 		 						 + "&merchantProductId="+merchantProductId;
    	
    	String productString = requestsCreator.createGetRequest(apiUrl, "partnersProductss?"+requestParameters);
    	
    	if(productString.isEmpty())
    		return new tblPartnersProducts();

    	return this.mapper.readValue(productString, tblPartnersProducts.class);
	}

    public Boolean delete(Integer id)
    {
    	try {
			return Boolean.parseBoolean(this.requestsCreator.createPostRequest(apiUrl, servletUrl+"action=delete", "{\"id\" : "+id+"}"));
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
    }
}
