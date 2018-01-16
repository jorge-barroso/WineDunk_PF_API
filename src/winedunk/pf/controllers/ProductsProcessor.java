package winedunk.pf.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import winedunk.pf.models.tblPartnersProducts;
import winedunk.pf.runnables.ProductsProcessRunnable;
import winedunk.pf.services.PartnersProductsService;
import winedunk.pf.services.RequestsCreator;
import winedunk.pf.services.WineService;

/**
 * Servlet implementation class ProductsProcessor
 */
@WebServlet(urlPatterns="/ProductsProcessor", asyncSupported=true)
public class ProductsProcessor extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private final Properties properties = new Properties();
	private final Date executionDate = new Date();
	private PartnersProductsService partnersProductsService;
	private WineService wineService;

	public ProductsProcessor() { super(); }

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
		this.partnersProductsService = new PartnersProductsService(properties.getProperty("crud.url"));
		this.wineService = new WineService(properties);
		//runnable = new ProductsProcessRunnable(properties, executionDate);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setStatus(HttpServletResponse.SC_ACCEPTED);

		//if there's something in the request body, we map it as Json, otherwise we just set the JsonNode as a new, empty one
		byte[] requestBytes = new byte[20];
		request.getInputStream().read(requestBytes, 0, 20);

		new Thread(new Runnable() {
			final ObjectMapper mapper = new ObjectMapper();
			@Override
			public void run() {
				JsonNode requestBody;
				try {
					//Create a JsonNode with the parameters sent in the request (presumably id of the product feed which products we want to import) if something is present 
					requestBody = new String(requestBytes).trim().length()>0 ? this.mapper.readTree(request.getInputStream()) : this.mapper.createObjectNode();
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

				//Executor to process each product
				//Executors.newSingleThreadExecutor();
				final ExecutorService executor = Executors.newSingleThreadExecutor();//Runtime.getRuntime().availableProcessors()-1);
				final List<Future<Integer>> futures = new ArrayList<Future<Integer>>(products.size());
				Integer j = 1;
				final Set<Tblpf> pfs = new HashSet<Tblpf>();
				//loop through each product returned by getProductsList (Might be related to a single pf if id is given or all of them if id is null)

				for(Tblpfproduct product : products)
				{
					pfs.add(product.getTblpf());
					//If we are currently processing that product feed we will wait until it has finished, unless it's a manual execution for a specific one, then we just skip it
					if(product.getTblpf().getLatestStatus().getName().equals(PfStatus.PROCESSING))
					{
						continue;
						/*if(!requestBody.has("id"))
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
							} while (product.getTblpf().getLatestStatus().getName().equals(PfStatus.PROCESSING));
						}*/
					}

					futures.add(executor.submit(new ProductsProcessRunnable(j++, properties, executionDate, product)));
				}
				//close executor after everything has finished
				try {
					executor.awaitTermination(1l, TimeUnit.DAYS);
				} catch (InterruptedException e) {
					System.out.println("An exception occured while internally waiting for all products to be processed");
					e.printStackTrace();
				}
				executor.shutdown();

				//Get the list of IDs contained in the futures
				final List<Integer> processedProducts = new ArrayList<Integer>(futures.size());
				for(Future<Integer> future : futures)
				{
					try {
						processedProducts.add(future.get());
					} catch (InterruptedException e) {
						e.printStackTrace();
						return;
					} catch (ExecutionException e) {
						e.printStackTrace();
						return;
					}
				}
				//Get a list with all the products in the database and set as deleted those that are not present in the list of processed IDs
				try {
					List<tblPartnersProducts> partnerProducts = partnersProductsService.getAll();
					List<Integer> existingWineIds = new ArrayList<Integer>(partnerProducts.size());
					List<Integer> allWineIds = new ArrayList<Integer>(partnerProducts.size());
					for(tblPartnersProducts partnerProduct: partnerProducts)
					{
						if(!processedProducts.contains(partnerProduct.getId()))
						{
							partnerProduct.setDeleted(true);
							partnersProductsService.delete(partnerProduct.getId());
						}
						else
						{
							existingWineIds.add(partnerProduct.getTblWines().getId());
						}
						allWineIds.add(partnerProduct.getTblWines().getId());
					}

					for(Integer wineId : allWineIds)
					{
						if(!existingWineIds.contains(wineId))
							wineService.delete(wineId);
					}
				} catch (JsonParseException e1) {
					e1.printStackTrace();
					return;
				} catch (JsonMappingException e1) {
					e1.printStackTrace();
					return;
				} catch (IOException e1) {
					e1.printStackTrace();
					return;
				}

				Timestamp time = new Timestamp(new Date().getTime());
				//Update each product feed with last importation status and time
				for(Tblpf pf : pfs)
				{
					pf.setLastImportation(time);
					try {
						RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductFeeds?action=update", this.mapper.writeValueAsString(pf), null);
					} catch (IOException e) {
						System.out.println("There was an exception while reaching the crud to set last importation status");
						e.printStackTrace();
					}

					//if we make it here, the process of importing the ProductFeed was successful and thus we can set the status tu OK
					try {
						RequestsCreator.createGetRequest(properties.getProperty("crud.url"), "ProductFeeds?action=okImportation&id="+pf.getId(), null);
					} catch (IOException e) {
						System.out.println("There was an exception while reaching the crud to set last importation status");
						e.printStackTrace();
					}
				}

				//final call to the crud to run the stored procedure that will update the minimum prices
				try {
					RequestsCreator.createGetRequest(properties.getProperty("crud.url"), "/wines?action=setMinimumPrices", null);
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

				List<Tblpfproduct> products = this.mapper.readValue(RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "Products?"+action, body, null), new TypeReference<List<Tblpfproduct>>(){});
				return products;
		    }
		}).start();
	}
}