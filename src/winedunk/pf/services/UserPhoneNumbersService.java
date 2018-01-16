package winedunk.pf.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import winedunk.pf.models.userPhoneNumbers;

public class UserPhoneNumbersService {
	
	private String relUrl;
	public String getRelUrl() { return relUrl; }
	public void setRelUrl(String relUrl) { this.relUrl = relUrl; }
	
	private String urlPath;
	public String getUrlPath() { return urlPath; }
	public void setUrlPath(String urlPath) { this.urlPath = urlPath; }
	
	private Integer userId;
	public Integer getUserId() { return userId; }
	public void setUserId(Integer userId) { this.userId = userId; }
	
	
	public List<userPhoneNumbers> loadPhoneNumbers() throws IOException
	{
		relUrl = "userPhoneNumbers?action=getUserPhoneNumbersForUser";
		String response = RequestsCreator.createPostRequest(urlPath, relUrl, userId.toString(), null);
		
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		
	   	JsonNode responseJson = mapper.readTree(response);
	   	if(responseJson == null) { return null; }
	   	
	   	ArrayNode phonesNodes = (ArrayNode) responseJson.get("PhoneNumbers");
	   	Iterator<JsonNode> phoneNumbersIterator = phonesNodes.elements();
   	 	List<userPhoneNumbers> resultsList = new ArrayList<userPhoneNumbers>();
   	 	
	   	 while(phoneNumbersIterator.hasNext())
		 {
			 JsonNode phoneNumberNode = phoneNumbersIterator.next();
			 userPhoneNumbers phoneNumber = mapper.treeToValue(phoneNumberNode, userPhoneNumbers.class);
			 resultsList.add(phoneNumber);
		 }
		
		 return resultsList;
	}
	
	public Boolean addPhoneNumber(String phoneNumber) throws IOException
	{
		relUrl = "userPhoneNumbers?action=addUserPhoneNumber";
		String contentString = "{ "
				+ "\"userId\" : " + userId + ", "
				+ "\"phoneNumber\" : \"" + phoneNumber + "\" }";
		
		String response = RequestsCreator.createPostRequest(urlPath, relUrl, contentString, null);
		
		if (response.equalsIgnoreCase("true")) { return true; }
		return false;
	}
	
	public Boolean editPhoneNumber(String phoneNumber, String numberId) throws IOException
	{
		relUrl = "userPhoneNumbers?action=updateUserPhoneNumber";
		String contentString = "{ "
				+ "\"id\" : " + numberId + ", "
				+ "\"userId\" : " + userId + ", "
				+ "\"phoneNumber\" : \"" + phoneNumber + "\" }";
		
		String response = RequestsCreator.createPostRequest(urlPath, relUrl, contentString, null);
		
		if(response.equalsIgnoreCase("true")) { return true; }
		return false;
	}
	
	public Boolean deletePhoneNumber(String id) throws IOException
	{
		relUrl = "userPhoneNumbers?action=deleteUserPhoneNumber";
		String response = RequestsCreator.createPostRequest(urlPath, relUrl, id, null);
		
		if(response.equalsIgnoreCase("true")) { return true; }
		return false;
	}
}
