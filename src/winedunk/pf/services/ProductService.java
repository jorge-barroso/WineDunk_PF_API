package winedunk.pf.services;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import winedunk.pf.helpers.Utils;
import winedunk.pf.models.DataSource;
import winedunk.pf.models.DataSourceParam;
import winedunk.pf.models.Tblpfproduct;
import winedunk.pf.models.viewWinePriceComparison;
import winedunk.pf.models.viewWinesMinimumPrice;

public class ProductService {
    
	private String urlPath;
	public String getUrlPath() { return urlPath; }
	public void setUrlPath(String urlPath) { this.urlPath = urlPath; }
	
	private viewWinesMinimumPrice wine;
 	public void setWine(viewWinesMinimumPrice wine) { this.wine = wine; }

 	private List<viewWinePriceComparison> priceComparisonList;
	public List<viewWinePriceComparison> getPriceComparisonList() { return priceComparisonList; }
	public void setPriceComparisonList(List<viewWinePriceComparison> priceComparisonList) { this.priceComparisonList = priceComparisonList; }
	
	public ProductService() {}
    
    public viewWinesMinimumPrice getWine(String id)
    {
    	try
    	{
    		//Create request
    		String relURL = "winesMinPriceView?action=getWine&id=" + id;
    		String responseString = RequestsCreator.createGetRequest(urlPath, relURL, null);
		
    		//Convert to object
			ObjectMapper mapper = new ObjectMapper();
			JsonNode responseJson = mapper.readTree(responseString);
			if(responseJson == null) { return null; }
			
			wine = mapper.treeToValue(responseJson, viewWinesMinimumPrice.class);
    	} catch (Exception e) { e.printStackTrace();}
		return wine;
    }

    public List<viewWinePriceComparison> getPriceComparison(String id)
    {
    	try
    	{
    		//Create request
    		String relURL = "winePriceComparisonView?action=getComparisonWithQuery&id=" + id;
    		String responseString = RequestsCreator.createGetRequest(urlPath, relURL, null);
    		//Convert to object
			ObjectMapper mapper = new ObjectMapper();
			JsonNode responseJson = mapper.readTree(responseString);
			if(responseJson == null) { return null; }

			ArrayNode winesNodes = (ArrayNode) responseJson.get("Shops");
			Iterator<JsonNode> winesIterator = winesNodes.elements();
			
			priceComparisonList = new ArrayList<viewWinePriceComparison>();
			while(winesIterator.hasNext())
			{
				JsonNode wineNode = winesIterator.next();
				viewWinePriceComparison shop = mapper.treeToValue(wineNode, viewWinePriceComparison.class);
				priceComparisonList.add(shop);
			}
    	} catch(Exception e) { e.printStackTrace(); }
		return priceComparisonList;
    }

    public String getFinalProductUrl(Tblpfproduct product, DataSource dataSource) throws MalformedURLException
    {
    	String dataUrl = Utils.getOrDefault(dataSource.getDataUrl(), product.getProductURL());

    	List<DataSourceParam> dataSourceParams = dataSource.getDataSourceParams();

    	if(dataSourceParams.isEmpty())
			return dataUrl;

		URL url = new URL(product.getProductURL());

		//As paths start with a first slash, this means that index 1 of the array will be the first effective section as well
		String[] pathSections = url.getPath()==null ? new String[0] : url.getPath().split("\\/");
		System.out.println(Arrays.asList(pathSections));

		//Parse query string into a map
		String[] queryParams = url.getQuery()==null ? new String[0] : url.getQuery().split("\\&");
		Map<String, String> queryParamsMap = new HashMap<String, String>();
		for(String queryParam : queryParams)
		{
			String[] queryParamSection = queryParam.split("\\=");
			queryParamsMap.put(queryParamSection[0], queryParamSection[1]);
		}

		//Replace final url parameters
		for(DataSourceParam dataSourceParam : dataSourceParams)
		{
			if(!product.getMerchantName().equals(dataSourceParam.getTblShops().getName()))
				continue;

			dataUrl = dataUrl.replace("${"+dataSourceParam.getParamName()+"}", 
									  dataSourceParam.getPathSection()!=null ? pathSections[dataSourceParam.getPathSection()] : queryParamsMap.get(dataSourceParam.getVariablesName()));
		}

		return dataUrl;
    }
}
