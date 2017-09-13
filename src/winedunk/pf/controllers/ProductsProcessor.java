package winedunk.pf.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.net.ftp.FTPClient;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.services.ProductsProcessRunnable;
import winedunk.pf.services.RequestsCreator;
import winedunk.pf.models.Tblpfproduct;
import winedunk.pf.helpers.PfStatus;

//TODO set all properties
/**
 * Servlet implementation class ProductsProcessor
 */
@WebServlet(urlPatterns="/ProductsProcessor", asyncSupported=true)
public class ProductsProcessor extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private final Properties properties;
	private final FTPClient ftp = new FTPClient();
	private final Date executionDate = new Date();

	public ProductsProcessor() throws IOException {
        super();
        this.properties = new Properties();

        try {
        	this.ftp.connect(properties.getProperty("ftp.host.address"));
		} catch (IOException e) {
			System.out.println("Something went wrong while reaching the ftp server");
			e.printStackTrace();
			return;
		}
        try {
        	this.ftp.login(properties.getProperty("ftp.username"), properties.getProperty("ftp.password"));
		} catch (IOException e) {
			System.out.println("Something went wrong while loging in to the ftp server");
			e.printStackTrace();
			return;
		}
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setStatus(HttpServletResponse.SC_ACCEPTED);

		try {
			properties.load(new FileInputStream(new File(getServletContext().getRealPath("/WEB-INF/productFeed.properties"))));
		} catch (FileNotFoundException e2) {
			System.out.println("The properties file could not be found");
			e2.printStackTrace();
			return;
		} catch (IOException e2) {
			System.out.println("I/O exception while attempting to read the properties file");
			e2.printStackTrace();
			return;
		}

		final AsyncContext async = request.getAsyncContext();
		async.start(new Runnable() {

			@Override
			public void run() {
				final List<Future<Integer>> futures = new ArrayList<Future<Integer>>();

				try {
					//Executor to process each product
					final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()/3*2);
					//List of products
					List<Tblpfproduct> products;
					try {
						products = getProductsList(Integer.valueOf(request.getParameter("id")));
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

					//loop through each product returned by getProductsList (Might be related to a single pf if id is given or all of them if id is null)
					for(Tblpfproduct product : products)
					{
						//If we are currently processing that product feed we will wait until it has finished, unless it's a manual execution for a specific one, then we just skip it
						if(product.getTblpf().getLatestStatus().getName().equals(PfStatus.PROCESSING.toString()))
						{
							if(!request.getParameterMap().containsKey("id"))
							{
								continue;
							}
							else
							{
								while (product.getTblpf().getLatestStatus().getName().equals("Processing")) 
								{
									try {
										Thread.sleep(5000);
									} catch (InterruptedException e) {
										System.out.println("Thread interrupted while waiting to process product feed");
										e.printStackTrace();
										return;
									}}
							}
						}
						futures.add(executor.submit(new ProductsProcessRunnable(product, properties, executionDate, ftp)));
					}
					//close executor after everything has finished
					executor.shutdown();
					try {
						executor.awaitTermination(1l, TimeUnit.DAYS);
					} catch (InterruptedException e) {
						System.out.println("An exception occured while internally waiting for all products to be processed");
						e.printStackTrace();
					}
				} finally {
					//close ftp connection whatever happens
					try {
						ftp.logout();
						ftp.disconnect();
					} catch (IOException e) {
						System.out.println("Unable to close server connection");
						e.printStackTrace();
					}
				}
				
				final Map<Integer, Integer> numberOfProductsPerPF = new HashMap<Integer, Integer>();
				for(Future<Integer> future : futures)
				{
					try {
						if(numberOfProductsPerPF.containsKey(future.get()))
							numberOfProductsPerPF.put(future.get(), numberOfProductsPerPF.get(future.get())+1);
						else
							numberOfProductsPerPF.put(future.get(), 1);
					} catch (InterruptedException e) {
						System.out.println("The process had been interrupted when/while counting the wines imported");
						e.printStackTrace();
					} catch (ExecutionException e) {
						System.out.println("An unhandled exception was thrown while processing one of the products");
						e.printStackTrace();
					}
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
		    	String parameters = id==null ? "action=getAll" : "action=getByPfId&pfId="+id;

				String productsJson = new RequestsCreator().createPostRequest(properties.getProperty("crud.url"), "Products", parameters);

				return new ObjectMapper().readValue(productsJson, new TypeReference<List<Tblpfproduct>>(){});
		    }
		});
	}
}