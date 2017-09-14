package winedunk.pf.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.quartz.CronExpression;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.winedunk.fileutils.Zip;

import winedunk.pf.models.Tblpf;
import winedunk.pf.models.Tblpfmapping;
import winedunk.pf.models.Tblpfproduct;
import winedunk.pf.services.RequestsCreator;


/**
 * Servlet implementation class ProductFeedsPocessor
 */
@WebServlet(urlPatterns="/ProductFeedsPocessor", asyncSupported=true)
public class ProductFeedsPocessor extends HttpServlet {
	private static final long serialVersionUID = 1L;
;
	private int productFeedsAmount;
	private CountDownLatch latch;
	private final Properties properties = new Properties();
	private final BlockingQueue<Tblpf> productFeedsToProcess = new ArrayBlockingQueue<Tblpf>(Runtime.getRuntime().availableProcessors());
	private final RequestsCreator requestsCreator = new RequestsCreator();
	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * 
	 */
    public ProductFeedsPocessor() {
        super();
        try {
			properties.load(new FileInputStream(new File(this.getServletContext().getRealPath("/WEB-INF/productFeed.properties"))));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
    }

    /**
     * 
     */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		//TODO change lastStandardisation date from every processed pf
		
		//if we have an id it's because it was called manually, so we quickly process it
		if(request.getParameter("id")!=null)
		{
			/*TODO CRUD to get product feed by id to be used here
			this.processProductFeed(Integer.valueOf(request.getParameter("id")));*/
		}
		else
		{
			//Thread one to populate list
			Thread t1 = new Thread(new Runnable() {
				@Override
				public void run() {
					populateList();
				}
			});
			//Thread two to take values from it
			Thread t2 = new Thread(new Runnable() {
				@Override
				public void run() {
					doWork();
				}
			});

			//initialize latch to make thread two wait until total amount of product feeds to be processed is declared by thread one
			this.latch = new CountDownLatch(1);
			
			//start threads
			t1.start();

			try {
				latch.await();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			t2.start();
		}
	}

	/**
	 * 
	 */
	private void populateList()
	{
		Date currentDate = new Date();
		
		ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()/2);
		//Map response to a List of Tblpf objects
	    ObjectMapper mapper = new ObjectMapper();
		String productFeeds;
		List<Tblpf> allProductFeedsList;
		try {
			productFeeds = new RequestsCreator().createPostRequest(properties.getProperty("crud.url"), "ProductFeeds", "action=getAll");
			allProductFeedsList = mapper.readValue(productFeeds, mapper.getTypeFactory().constructCollectionType(List.class, Tblpf.class));
		} catch (IOException e2) {
			e2.printStackTrace();
			return;
		}

		//set amount of productfeeds to be processed and liberate latch so the second thread is able to start
		this.productFeedsAmount = allProductFeedsList.size();
		this.latch.countDown();
		for(Tblpf pf : allProductFeedsList)
		{

			executor.execute(new Runnable() {

				@Override
				public void run() {
					try {
						if(pf.getStartTime().getTime() > Calendar.getInstance().getTimeInMillis())
							return;

						CronExpression expression;
						//Analyse cron, set as failed if there's a problem  parsing it
						//TODO Set the error to the log table
						try {
							expression = new CronExpression(pf.getTimePeriod());
						} catch (ParseException e) {
							e.printStackTrace();
							try {
								new RequestsCreator().createPostRequest(properties.getProperty("crud.url"), "ProductFeeds", "action=fail&id="+pf.getId());
							} catch (IOException e1) {
								e1.printStackTrace();
							}
							return;
						}

						//get last standardisation timestamp and set it as a calendar to get the next execution time after it
						Timestamp timestamp = pf.getLastStandardisation();
						Calendar lastExecution = Calendar.getInstance();
						lastExecution.setTimeInMillis(timestamp.getTime());
						//if the next execution time is in the past, then we process it now
						if(expression.getNextValidTimeAfter(lastExecution.getTime()).getTime() <= currentDate.getTime())
							productFeedsToProcess.add(pf);
					} finally {
						synchronized(new Object()) { productFeedsAmount--; }
					}
				}
			});
		}
	}

	/**
	 * 
	 */
	private void doWork()
	{
		ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()/2);
		while(this.productFeedsAmount>0 || !this.productFeedsToProcess.isEmpty())
		{
			Tblpf pf;
			try {
				pf = productFeedsToProcess.take();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
			executor.execute(new Runnable() {

				@Override
				public void run() {
						try {
							processProductFeed(pf);
							return;
						} catch (MalformedURLException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						//if we make it here an exception during processProductFeed impeded returning so we flag the pf as failed
						try {
							new RequestsCreator().createPostRequest(properties.getProperty("crud.url"), "ProductFeeds", "action=fail&id="+pf.getId());
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
				}
				
			});
		}
		executor.shutdown();
	}

	/**
	 * 
	 * @param id
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public void processProductFeed(Tblpf pf) throws MalformedURLException, IOException
	{
		String pfMappingJson = this.requestsCreator.createGetRequest(properties.getProperty("crud.url"), "Tblpfmapping?action=getByPFId&id="+pf.getId());
		Tblpfmapping pfMapping = this.mapper.readValue(pfMappingJson, Tblpfmapping.class);  

		File files[];
		String pfName = "productFeed"+pf.getId();
		String zipName = "pf.zip"+pf.getId();
		URL feedURL = new URL(pf.getDownloadURL());
		if(pf.getIsZip())
		{
			try (ReadableByteChannel channel = Channels.newChannel(feedURL.openStream());
				FileOutputStream outputFile = new FileOutputStream(zipName)) 
			{
				outputFile.getChannel().transferFrom(channel, 0, Long.MAX_VALUE);
			}

			Zip zip = new Zip();
			zip.unzip(zipName, pfName);
			new File(zipName).delete();
		}
		else
		{
			try (ReadableByteChannel channel = Channels.newChannel(feedURL.openStream());
					FileOutputStream outputFile = new FileOutputStream(pfName)) 
				{
					outputFile.getChannel().transferFrom(channel, 0, Long.MAX_VALUE);
				}
		}

		//get a list of files
		files = new File(pfName).isDirectory() ? new File(pfName).listFiles() : new File[] {new File(pfName)};

		for(File file : files)
		{
			char separator = pf.getSeparator().equals("\\t") ? '\t' : pf.getSeparator().charAt(0);
			CSVParser parser = new CSVParserBuilder().withSeparator(separator).build();

			try(CSVReader reader = new CSVReaderBuilder(new FileReader(file)).withSkipLines(pf.getHasHeader() ? 1 : 0).withCSVParser(parser).build())
			{
				//in this list we will store the IDs of the products we process (to set te old products as deleted)
				final List<Integer> productsFound = Collections.synchronizedList(new ArrayList<Integer>());
				ExecutorService executor = Executors.newFixedThreadPool(20);
	
				String[] lineValues;
				while((lineValues = reader.readNext())!= null)
				{
					final String[] finalValues = lineValues;
					executor.submit(new Runnable() {
	
						@Override
						public void run() {
							Tblpfproduct product;
							try {
								String parameters = "partnerProductId="+finalValues[pfMapping.getPartnerProductIdColumn()]
										  		  + "&merchantProductId="+finalValues[pfMapping.getMerchantProductIdColumn()];

								product = mapper.readValue(requestsCreator.createPostRequest(properties.getProperty("crud.url"), "Products?action=findByPartnerProductIdAndMerchantProductId", parameters), Tblpfproduct.class);
							} catch (JsonParseException e) {
								System.out.println("While trying to get the possibly existing product from the CRUD before updating/inserting it, the response provided by this one doesn't seem to have a proper JSON format");
								e.printStackTrace();
								return;
							} catch (JsonMappingException e) {
								System.out.println("While trying to get the possibly existing product from the CRUD before updating/inserting it, the response provided by this one couldn't be mapped to a Tblpfproduct object");
								e.printStackTrace();
								return;
							} catch (IOException e) {
								System.out.println("While trying to get the possibly existing product from the CRUD before updating/inserting it, couldn't reach the CRUD or a low-level I/O exception occurred, please check the server");
								e.printStackTrace();
								return;
							}

							//populate values from the file using the mapping
							product.setClicktag(finalValues[pfMapping.getClicktagColumn()]);
							product.setDeliveryCost(Float.valueOf(finalValues[pfMapping.getDeliveryCostColumn()]));
							product.setImageURL(finalValues[pfMapping.getImageURLColumn()]);
							product.setMerchantName(finalValues[pfMapping.getMerchantNameColumn()]);
							product.setMerchantProductId(finalValues[pfMapping.getMerchantProductIdColumn()]);
							product.setName(finalValues[pfMapping.getNameColumn()]);
							product.setPartnerMerchantId(finalValues[pfMapping.getPartnerMerchantIdColumn()]);
							product.setPartnerProductDescription(finalValues[pfMapping.getPartnerProductDescriptionColumn()]);
							product.setPartnerProductId(finalValues[pfMapping.getPartnerProductIdColumn()]);
							product.setPrice(Float.valueOf(finalValues[pfMapping.getPriceColumn()]));
							product.setProductType(finalValues[pfMapping.getWineTypeColumn()]);
							product.setTblpf(pf);
	
							//persist only if it's a new product
							if(product.getId()==null)
							{
								try {
									product.setId(Integer.parseInt(requestsCreator.createPostRequest(properties.getProperty("crud.url"), "Products?action=addProduct", mapper.writeValueAsString(product))));
								} catch (NumberFormatException e) {
									System.out.println("After adding the product to the database, the id returned doesn't seem to be a valid number");
									e.printStackTrace();
								} catch (JsonProcessingException e) {
									System.out.println("Whiles trying to add a new product to the database, there was a problem while serialising it");
									e.printStackTrace();
								} catch (IOException e) {
									System.out.println("Whiles trying to add a new product to the database, the CRUD wasn't reachable");
									e.printStackTrace();
								}
							}
							else
							{
								try {
									Boolean updated = Boolean.parseBoolean(requestsCreator.createPostRequest(properties.getProperty("crud.url"), "Products?action=updateProduct", mapper.writeValueAsString(product)));
									if(!updated)
										System.out.println("Something went wrong updating the wine on the database");
								} catch (JsonProcessingException e) {
									System.out.println("Whiles trying to update a product on the database, there was a problem while serialising it");
									e.printStackTrace();
								} catch (IOException e) {
									System.out.println("Whiles trying to update a product on the database, the CRUD wasn't reachable");
									e.printStackTrace();
								}
							}
	
							productsFound.add(product.getId());
						}
	
					});
	
					executor.shutdown();
					try {
						executor.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
						//if the tread is interrupted we just return without updating the last importation date, as the thread failed
						return;
					}
	
					//Get all the listed products from this parter and remove those which id is not contaied in the list with the current products' ids
					String productsListJson = this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "Products?action=getByPfId", "id="+pf.getId());
					List<Tblpfproduct> productsList = this.mapper.readValue(productsListJson, new TypeReference<List<Tblpfproduct>>(){});
					for(Tblpfproduct pfProduct : productsList)
					{
						if(!productsFound.contains(pfProduct.getId()))
						{
							if(!Boolean.parseBoolean(this.requestsCreator.createPostRequest(properties.getProperty("crud.url"), "Products?action=deleteProduct", "id="+pf.getId())))
								System.out.println("Couldn't delete wine "+pf.getId());
						}
					}
	
					//set last importation timestamp to current date
					pf.setLastImportation(new Timestamp(new Date().getTime()));
				}
			}
		}
	}
}
