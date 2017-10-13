package winedunk.pf.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.helpers.PfStatus;
import winedunk.pf.models.Tblpf;
import winedunk.pf.models.Tblpfproduct;
import winedunk.pf.services.ProductsProcessRunnableSynchronous;
import winedunk.pf.services.RequestsCreator;

//TODO set all properties
/**
 * Servlet implementation class ProductsProcessor
 */
@WebServlet(urlPatterns="/ProductsProcessorSynchronous", asyncSupported=true)
public class ProductsProcessorSynchronous extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private final Properties properties = new Properties();
	private final Date executionDate = new Date();
	private final RequestsCreator requestsCreator = new RequestsCreator();
	private ProductsProcessRunnableSynchronous runnable;
	public ProductsProcessorSynchronous() {
        super();
    }

	/**
	 * We use the init method (the default post construct) as we don't have the ServletContext in the constructor
	 */
	@Override
	public void init() {
		try {
			this.properties.load(new FileInputStream(new File(this.getServletContext().getRealPath("/WEB-INF/productFeed.properties"))));
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}
		runnable = new ProductsProcessRunnableSynchronous(properties, executionDate);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setStatus(HttpServletResponse.SC_ACCEPTED);

		//if there's something in the request body, we map it as Json, otherwise we just set the JsonNode as a new, empty one
		byte[] requestBytes = new byte[20];
		request.getInputStream().read(requestBytes, 0, 20);

		final List<Integer> futures = new ArrayList<Integer>();
		JsonNode requestBody;
		try {
			//Create a JsonNode with the parameters sent in the request (presumably id of the product feed which products we want to import) if something is present 
			requestBody = new String(requestBytes).trim().length()>0 ? new ObjectMapper().readTree(request.getInputStream()) : new ObjectMapper().createObjectNode();
		} catch (JsonProcessingException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
			return;
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
			return;
		}

		//List of products
		List<Tblpfproduct> products;
		try {
			products = getProductsList(requestBody.has("id") ? requestBody.get("id").asInt() : null);
		} catch (JsonParseException e1) {
			System.out.println("While trying to get a list with all the products (or only those from the specified product feed if requested manually), the response from the CRUD doesn't seem to have a valid JSON format");
			e1.printStackTrace();
			return;
		} catch (JsonMappingException e1) {
			System.out.println("While trying to get a list with all the products (or only those from the specified product feed if requested manually), the response from the CRUD couldn't be mapped to a proper List of Tblpfproduct");
			e1.printStackTrace();
			return;
		} catch (NumberFormatException e1) {
			System.out.println("The ProductFeed id provided was not a proper number");
			e1.printStackTrace();
			return;
		} catch (IOException e1) {
			System.out.println("Couldn't reach the CRUD or there was a low-level I/O error, please check the server");
			e1.printStackTrace();
			return;
		}

		Integer j = 1;
		Set<Tblpf> pfs = new HashSet<Tblpf>(10);
		//loop through each product returned by getProductsList (Might be related to a single pf if id is given or all of them if id is null)
		for(Tblpfproduct product : products)
		{
			pfs.add(product.getTblpf());
			//If we are currently processing that product feed we will wait until it has finished, unless it's a manual execution for a specific one, then we just skip it
			if(product.getTblpf().getLatestStatus().getName().equals(PfStatus.PROCESSING.toString()))
			{
				if(!requestBody.has("id"))
				{
					continue;
				}
				else
				{
					do 
					{
						try {
							Thread.sleep(5000);
						} catch (InterruptedException e) {
							System.out.println("Thread interrupted while waiting to process product feed");
							e.printStackTrace();
							return;
						}
					} while (product.getTblpf().getLatestStatus().getName().equals(PfStatus.PROCESSING.toString()));
				}
			}
			//System.out.println("ADDING WINE NUMBER "+j);
			//futures.add(executor.submit(new ProductsProcessRunnable(j++, properties, executionDate, product)));
			try {
				futures.add(runnable.call(j++, product));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		final Map<Integer, Integer> numberOfProductsPerPF = new HashMap<Integer, Integer>();
		for(Integer productId : futures)
		{
			if(numberOfProductsPerPF.containsKey(productId) && productId!=null)
				numberOfProductsPerPF.put(productId, numberOfProductsPerPF.get(productId)+1);
			else
				numberOfProductsPerPF.put(productId, 1);
		}

		Timestamp time = new Timestamp(new Date().getTime());
		//Update each product feed with last importation status and time
		for(Tblpf pf : pfs)
		{
			pf.setLastImportation(time);
			try {
				this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductFeeds?action=update", new ObjectMapper().writeValueAsString(pf));
			} catch (IOException e) {
				System.out.println("There was an exception while reaching the crud to set last importation status");
				e.printStackTrace();
			}

			try {
				this.requestsCreator.createGetRequest(properties.getProperty("crud.url"), "ProductFeeds?action=okImportation&id="+pf.getId());
			} catch (IOException e) {
				System.out.println("There was an exception while reaching the crud to set last importation status");
				e.printStackTrace();
			}
		}

		try {
			this.requestsCreator.createGetRequest(properties.getProperty("crud.url"), "/wines?action=setMinimumPrices");
		} catch (IOException e) {
			System.out.println("There was an exception while reaching the crud to execute the internal stored procedure to update the minimum wine prices");
			e.printStackTrace();
		}
	}

	/**
     * 
     * @param id
     * @return
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonParseException 
     */
    private List<Tblpfproduct> getProductsList(Integer id) throws JsonParseException, JsonMappingException, IOException
    {
    	String action;
    	String body;
    	if(id==null)
    	{
    		action = "action=getAll";
    		body = "{ }";
    	}
    	else
    	{
    		action = "action=getByPfId";
    		body = "{ \"pfId\" : "+id+" }";
    	}

		List<Tblpfproduct> products = new ObjectMapper().readValue(new RequestsCreator().createPostRequest(properties.getProperty("crud.url"), "Products?"+action, body), new TypeReference<List<Tblpfproduct>>(){});
		return products;
    }
}