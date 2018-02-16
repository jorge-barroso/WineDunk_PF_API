import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.services.RequestsCreator;

public class test {

	public static void main(String[] args) throws IOException
	{
		String token = new ObjectMapper().readTree(RequestsCreator.createGetRequest("https://www.waitrose.com/api/authentication-prod/v2/authentication/token", null)).findPath("jwtString").asText();

		System.out.println(token);
		Map<String, String> authHeader = new HashMap<String, String>();
		authHeader.put("authorization", token);
		System.out.println(RequestsCreator.createGetRequest("https://www.waitrose.com/api/custsearch-prod/v3/search/-1/573123-499324-499325", authHeader));
	}
}
