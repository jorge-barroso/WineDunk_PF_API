package winedunk.pf.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import winedunk.pf.models.tblPartners;
import winedunk.pf.runnables.ProductsProcessRunnable;
import winedunk.pf.services.PFLogService;
import winedunk.pf.services.PFLogTypesService;
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
	
	// aripe, logs management
	PFLogService pfLogService = new PFLogService();
	tblPartners partners = new tblPartners();
	private PFLogTypesService pfLogTypesService = new PFLogTypesService();	
	String logTypeErrorName = pfLogTypesService.getLogTypeError().getName();
	String logTypeWarningName = pfLogTypesService.getLogTypeWarning().getName();
	String logTypeInformationName = pfLogTypesService.getLogTypeInformation().getName();
	
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
		new PartnersProductsService(properties.getProperty("crud.url"));
		new WineService(properties);
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
				List<Tblpf> tblpfs;
				try {
					tblpfs = getProductFeeds(requestBody.has("id") ? requestBody.get("id").asInt() : null);
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
				
				//loop through each product returned by getProductsList (Might be related to a single pf if id is given or all of them if id is null)
				Map<Tblpf, Boolean> productFeedsStatus = new HashMap<Tblpf, Boolean>(tblpfs.size());
				for(Tblpf tblpf : tblpfs)
				{
					partners = tblpf.getPartnerId();
					pfLogService.ProductsProcessorBegin(partners);
					
					productFeedsStatus.put(tblpf, true);
					//If we are currently processing that product feed we will wait until it has finished, unless it's a manual execution for a specific one, then we just skip it
					if(tblpf.getLatestStatus().getName().equals(PfStatus.PROCESSING))
						continue;
					
					for(Tblpfproduct product : tblpf.getTblpfproducts())
					{
						//close executor after everything has finished
						try{
							executor.submit(new ProductsProcessRunnable(properties, executionDate, product, logTypeErrorName, logTypeWarningName, logTypeInformationName));
						} catch (Exception e) {
							productFeedsStatus.put(tblpf, false);
						}
					}
				}
				
				
				executor.shutdown();
				
				try {
					executor.awaitTermination(1l, TimeUnit.DAYS);
				} catch (InterruptedException e) {
					System.out.println("An exception occured while internally waiting for all products to be processed");
					e.printStackTrace();
				}
				

				Timestamp time = new Timestamp(new Date().getTime());
				//Update each product feed with last importation status and time
				for(Map.Entry<Tblpf, Boolean> tblpfStatus : productFeedsStatus.entrySet())
				{
					if(tblpfStatus.getValue())
					{
						Tblpf tblpf = tblpfStatus.getKey();

						tblpf.setLastImportation(time);
						try {
							RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductFeeds?action=update", this.mapper.writeValueAsString(tblpf), null);
						} catch (IOException e) {
							System.out.println("There was an exception while reaching the crud to set last importation status");
							e.printStackTrace();
						}
	
						//if we make it here, the process of importing the ProductFeed was successful and thus we can set the status tu OK
						try {
							RequestsCreator.createGetRequest(properties.getProperty("crud.url"), "ProductFeeds?action=okImportation&id="+tblpf.getId(), null);
						} catch (IOException e) {
							System.out.println("There was an exception while reaching the crud to set last importation status");
							e.printStackTrace();
						}
					}
				}
				
				
				// aripe 2018-04-28
				// calling the crud to execute stored procedure spUpdateDataAfterImporting
				try {
					RequestsCreator.createGetRequest(properties.getProperty("crud.url"), "/storedProcedures?action=callSPUpdateDataAfterImporting", null);
					pfLogService.StoredprocedureCalled(partners, "spUpdateDataAfterImporting()");
				} catch (Exception e) {
					System.out.println("There was an exception while reaching the crud to execute the internal stored procedure \"spUpdateDataAfterImporting()\"");
					e.printStackTrace();
				}
				
				// Inserting closing event Log
				pfLogService.ProductsProcessorEnd(partners);
								
			}

			/**
		     * 
		     * @param id
		     * @return
		     * @throws IOException 
		     * @throws JsonMappingException 
		     * @throws JsonParseException 
		     */
		    private List<Tblpf> getProductFeeds(Integer id) throws JsonParseException, JsonMappingException, IOException
		    {
		    	final String parameters = id==null ? "action=getAll" : "action=getById?id="+id;

				List<Tblpf> productFeeds = this.mapper.readValue(RequestsCreator.createGetRequest(properties.getProperty("crud.url"), "ProductFeeds?"+parameters, null), new TypeReference<List<Tblpf>>(){});
				
				return productFeeds;
		    }
		}).start();
	}
}