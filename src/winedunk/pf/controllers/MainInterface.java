package winedunk.pf.controllers;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.models.Tblpf;
import winedunk.pf.models.tblPartners;
import winedunk.pf.services.RequestsCreator;


/**
 * Servlet implementation class MainInterface
 */
@WebServlet({ "/Main", "/" })
public class MainInterface extends HttpServlet {
	private static final long serialVersionUID = 1L;

	Properties properties = new Properties();
	
    public MainInterface() {
    	super();
    }

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			properties.load(new FileInputStream(new File(this.getServletContext().getRealPath("/WEB-INF/productFeed.properties"))));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		/**if(request.getSession().getAttribute("userLoggedIn")==null)
		{
			String sourceUrl = URLEncoder.encode(properties.getProperty("rest.url")+request.getContextPath(), "UTF-8");
			response.sendRedirect(properties.getProperty("rest.url")+"Login?source="+sourceUrl);
			return;
		}**/

		RequestsCreator requestsCreator = new RequestsCreator();
		ObjectMapper mapper = new ObjectMapper();

		//Request full ProductFeeds list
		String productFeedsJson = requestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductFeeds", "action=getAllProductFeeds");
	    //Map response to a List of Tblpf objects
	    List<Tblpf> productFeeds = productFeedsJson.isEmpty() ? new ArrayList<Tblpf>() : mapper.readValue(productFeedsJson, new TypeReference<List<Tblpf>>(){});

	    String partnersListJson = requestsCreator.createGetRequest(properties.getProperty("crud.url"), "partners?action=getPartners");
	    List<tblPartners> tblPartners = partnersListJson.isEmpty() ? new ArrayList<tblPartners>() : mapper.readValue(partnersListJson, new TypeReference<List<tblPartners>>(){});


		//save for the JSTL template
		request.getSession().setAttribute("productFeeds", productFeeds);
		//go to jstl
		//TODO create main.jsp
		request.getRequestDispatcher("/WEB-INF/templates/main.jsp").forward(request, response);

		return;		
	}
}
