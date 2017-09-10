package winedunk.pf.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Calendar;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.models.Tblpf;
import winedunk.pf.services.RequestsCreator;


/**
 * Servlet implementation class ProductFeedsPocessor
 */
@WebServlet(urlPatterns="/ProductFeedsPocessor", asyncSupported=true)
public class ProductFeedsPocessor extends HttpServlet {
	private static final long serialVersionUID = 1L;

	Properties properties;
	int productFeedsAmount;
	CountDownLatch latch;
	int limit = 5;
	BlockingQueue<Tblpf> productFeedsToProcess = new ArrayBlockingQueue<Tblpf>(limit);


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
			this.processProductFeed(Integer.valueOf(request.getParameter("id")));
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
			CountDownLatch latch = new CountDownLatch(1);
			
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
		
		ExecutorService executor = Executors.newFixedThreadPool(this.limit);
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
		latch.countDown();
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
		ExecutorService executor = Executors.newFixedThreadPool(this.limit);
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
					processProductFeed(pf.getId());
				}
				
			});
		}
		executor.shutdown();
	}

	/**
	 * 
	 * @param id
	 */
	private void processProductFeed(int id)
	{
		try{
			//TODO send pf to be processed
			new RequestsCreator().createPostRequest(properties.getProperty("crud.url"), "ProductFeeds", "action=process&id="+id);
		} catch(Exception e) {
			try {
				new RequestsCreator().createPostRequest(properties.getProperty("crud.url"), "ProductFeeds", "action=fail&id="+id);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return;
		}
	}

}
