package winedunk.pf.services;

import java.io.IOException;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.models.viewUsers;

/**
 * Session Bean implementation class ValidationSerivce
 */

public class ValidationService {

	private String urlPath;
	public String getUrlPath() { return urlPath; }
	public void setUrlPath(String urlPath) { this.urlPath = urlPath; }
	
	private String relUrl;
    public String getRelUrl() { return relUrl; }
	public void setRelUrl(String relUrl) { this.relUrl = relUrl; }
	
	private viewUsers user;
	public viewUsers getUser() { return user; }
	
	public ValidationService() { } 
    
    public Boolean validateUser(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
		viewUsers userLoggedIn = (viewUsers) request.getSession().getAttribute("userLoggedIn");
		Cookie[] cookies = request.getCookies();
		Cookie ourCookie;
		//Check session
		if(userLoggedIn != null && userLoggedIn.getId() <= 0) { return false; }
		
		if (cookies != null) 
		{
			for (Cookie cookie : cookies) 
			{
				if (cookie.getName().equals("uvt")) 
				{
					ourCookie = cookie;
					//Create request
					relUrl = "users?action=getUserByAuth";
					
					String responseString = RequestsCreator.createPostRequest(urlPath, relUrl, ourCookie.getValue(), null);
					if(responseString == null || responseString.equals("null")) { return false; }
					ObjectMapper objectMapper = new ObjectMapper();
					user = objectMapper.readValue(responseString, viewUsers.class);
					// Compare token to DB and identify User
					if(!user.getLoginToken().equals(ourCookie.getValue())) { return false; }
					
					request.getSession().setAttribute("userLoggedIn", user);
					request.getSession().setAttribute("isLoggedIn", true);
					
					ourCookie.setMaxAge(60*60*24*90); // 3 months
					response.addCookie(ourCookie);
					return true;
				}
			}
		}
    	return false;
    }
}
 