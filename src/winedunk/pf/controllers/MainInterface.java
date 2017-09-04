package winedunk.pf.controllers;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.models.Tblpf;


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
		
		if(request.getSession().getAttribute("userLoggedIn")==null)
		{
			String sourceUrl = URLEncoder.encode(properties.getProperty("rest.url")+request.getContextPath(), "UTF-8");
			response.sendRedirect(properties.getProperty("rest.url")+"Login?source="+sourceUrl);
			return;
		}

		//Request full ProductFeeds list
		URL url = new URL(properties.getProperty("crud.url")+"ProductFeeds");
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
	    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

	    String parameters = "action=getAllProductFeeds";
	    connection.setRequestProperty("Content-Length", Integer.toString(parameters.getBytes().length));
	    connection.setRequestProperty("Content-Language", "en-GB");

	    connection.setUseCaches(false);
	    connection.setDoOutput(true);

	    //open stream with the request's results
	    DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
	    outputStream.writeBytes(parameters);
	    outputStream.close();

	    //get and store JSON response
	    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
	    StringBuffer stringBuffer = new StringBuffer();
	    String line;
	    while((line = reader.readLine()) != null)
	    {
	    	stringBuffer.append(line);
	    }
	    reader.close();

	    //Map response to a List of Tblpf objects
	    ObjectMapper mapper = new ObjectMapper();
	    List<Tblpf> productFeeds = mapper.readValue(stringBuffer.toString(), mapper.getTypeFactory().constructCollectionType(List.class, Tblpf.class));

		//save for the JSTL template
		request.getSession().setAttribute("productFeeds", productFeeds);
		//go to jstl
		//TODO create main.jsp
		request.getRequestDispatcher("/WEB-INF/templates/main.jsp").forward(request, response);

		return;		
	}
}
