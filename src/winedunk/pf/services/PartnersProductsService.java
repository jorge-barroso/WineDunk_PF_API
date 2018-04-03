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

    private final ObjectMapper mapper;
    private final String servletUrl;
    String apiUrl;

    /**
     * 
     */
    public PartnersProductsService(String apiUrl) {
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
    	return this.mapper.readValue(RequestsCreator.createGetRequest(apiUrl, servletUrl+"?action=getPartnersProductss", null), new TypeReference<List<tblPartnersProducts>>(){});
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
    	String response = RequestsCreator.createPostRequest(apiUrl, servletUrl+"?action=addPartnersProducts", this.mapper.writeValueAsString(product), null);
    	return Integer.parseInt(response);
    }

    /**
     * 
     * @param product
     * @return
     * @throws JsonProcessingException
     * @throws IOException
     */
    public Boolean updateProduct(Integer id, Float price) throws JsonProcessingException, IOException
    {
    	// aripe 2018-04-03
    	final String body = "{" + 
    	"\"id\" : \"" + id + "\"," + 
    	"\"partnerProductPrice\" : \"" + price + "\"" + 
    	"}";
    	
    	final String response = RequestsCreator.createPostRequest(apiUrl, servletUrl+"?action=updatePartnersProductsPrice", body, null);
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
    	
    	String productString = RequestsCreator.createGetRequest(apiUrl, "partnersProductss?"+requestParameters, null);
    	
    	if(productString.isEmpty())
    		return new tblPartnersProducts();

    	return this.mapper.readValue(productString, tblPartnersProducts.class);
	}

    // aripe 2018-03-31
    public tblPartnersProducts getProduct(Integer partnertId, String partnerProductId) throws IOException
    {
    	//get possibly existing product by partnertId and partnerProductId
    	String requestParameters = "action=getByPartnerIdAndPartnerProductId"
    							 + "&partnerId="+partnertId
 		 						 + "&partnerProductId="+partnerProductId;
    	
    	String productString = RequestsCreator.createGetRequest(apiUrl, "partnersProductss?"+requestParameters, null);
    	
    	if(productString.isEmpty())
    		return new tblPartnersProducts();

    	return this.mapper.readValue(productString, tblPartnersProducts.class);
	}

    public Boolean delete(Integer id)
    {
    	try {
			return Boolean.parseBoolean(RequestsCreator.createPostRequest(apiUrl, servletUrl+"action=delete", "{\"id\" : "+id+"}", null));
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
    }
}
