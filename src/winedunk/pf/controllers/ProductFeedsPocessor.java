package winedunk.pf.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    public ProductFeedsPocessor() {
        super();
        try {
			properties.load(new FileInputStream(new File(this.getServletContext().getRealPath("/WEB-INF/productFeed.properties"))));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
    }

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		Date currentDate = new Date();		

	    //Map response to a List of Tblpf objects
	    ObjectMapper mapper = new ObjectMapper();
	    String productFeeds = new RequestsCreator().createPostRequest(properties.getProperty("crud.url"), "ProductFeeds", "action=getAll");
	    List<Tblpf> allProductFeedsList = mapper.readValue(productFeeds, mapper.getTypeFactory().constructCollectionType(List.class, Tblpf.class));

	    //List with productfeeds to be processed
	    List<Tblpf> productFeedsToProcess = new ArrayList<Tblpf>();
	    ExecutorService executor = Executors.newFixedThreadPool(8);

		if(request.getParameter("id")==null)
		{
			for(Tblpf pf : allProductFeedsList)
			{
				executor.submit(new Runnable() {

					@Override
					public void run() {
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
							synchronized(new Object()) {productFeedsToProcess.add(pf);}
					}
					
				});

				executor.shutdown();
				try {
					executor.awaitTermination(1l, TimeUnit.DAYS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		else
		{
			String pfJson = new RequestsCreator().createPostRequest(properties.getProperty("crud.url"), "ProductFeeds", "action=getById&id="+request.getParameter("id"));
			productFeedsToProcess.add(new ObjectMapper().readValue(pfJson, Tblpf.class));
		}

		executor = Executors.newFixedThreadPool(5);
		for(Tblpf pf : productFeedsToProcess)
		{
			executor.submit(new Runnable() {

				@Override
				public void run() {
					try{
						//TODO send pf to be processed
						new RequestsCreator().createPostRequest(properties.getProperty("crud.url"), "ProductFeeds", "action=process&id="+pf.getId());
					} catch(Exception e) {
						try {
							new RequestsCreator().createPostRequest(properties.getProperty("crud.url"), "ProductFeeds", "action=fail&id="+pf.getId());
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						return;
					}
				}
				
			});
		}
	}

}
