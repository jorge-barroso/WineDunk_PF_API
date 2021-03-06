package winedunk.pf.services;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.models.tblUserFavouriteWines;

public class FavouriteWinesService {
	public FavouriteWinesService() {}
	
	private String relUrl;
	public String getRelUrl() { return relUrl; }
 	public void setRelUrl(String relUrl) { this.relUrl = relUrl; }
	
	private String urlPath;
	public String getUrlPath() { return urlPath; }
	public void setUrlPath(String urlPath) { this.urlPath = urlPath; }

	JsonChecker jsonChecker = new JsonChecker();
	
	/*
	 * This class manages all the actions that need to be taken with the favourite wines.
	 * These methods will be invoked from the Product servlet and will make requests to the CRUD API. 
	 */
	
	public Boolean addFavouriteWine(Integer userId, Integer wineId) throws IOException
	{
		relUrl = "userFavouriteWines?action=addUserFavouriteWine";

		String dateString = new SimpleDateFormat("yyyy-MM-dd").format((new Date()));
		String content = "{"
				+ " \"userId\" : " + userId + ","
				+ " \"wineId\" : " + wineId + ","
				+ " \"addedDate\" : \"" + dateString + "\", "
				+ " \"addedTimestamp\" : \"" + new Date().getTime()
				+ "\" }";
		
		String response = RequestsCreator.createPostRequest(urlPath, relUrl, content, null);
		if(response == null) { return false; }
		else if(response.equals("True")) { return true; }
		
		return false;
	}
	
	public Boolean deleteFavouriteWine(Integer userId, Integer wineId) throws IOException
	{
		/*
		 * Get the favourite wine to be deleted based on wineId and userId,
		 * Then we have the ID so we can delete it using another request
		 * To the CRUD 
		 */
		relUrl = "userFavouriteWines?action=getFavouriteWineForUser";
		String content = userId + "," + wineId;
		
		String response = RequestsCreator.createPostRequest(urlPath, relUrl, content, null);
		if(response == null) { return false; }
		
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		
	   	JsonNode responseJson = mapper.readTree(response);
	   	if(responseJson == null) { return false; }
	   	
	   	tblUserFavouriteWines wine = mapper.treeToValue(responseJson, tblUserFavouriteWines.class);
	   	if(wine == null) { return false; }
	   	
	   	Integer wineToBeDeletedId = wine.getId();
	   	
	   	relUrl = "userFavouriteWines?action=deleteUserFavouriteWine";
	   	String deleteResponse = RequestsCreator.createPostRequest(urlPath, relUrl, wineToBeDeletedId.toString(), null);
	   	
	   	if(deleteResponse == null) { return false; }
	   	return true;
	}
}
