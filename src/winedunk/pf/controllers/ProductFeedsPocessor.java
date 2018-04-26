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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.quartz.CronExpression;

import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.helpers.PfStatus;
import winedunk.pf.models.Tblpf;
import winedunk.pf.runnables.ProductFeedsRunnable;
import winedunk.pf.services.PFLogService;
import winedunk.pf.services.ProductFeedsProcessHelper;
import winedunk.pf.services.RequestsCreator;


/**
 * Servlet implementation class ProductFeedsPocessor
 */
@WebServlet(urlPatterns="/ProductFeedsPocessor", asyncSupported=true)
public class ProductFeedsPocessor extends HttpServlet {
	private static final long serialVersionUID = 1L;

	//initialize latch to make thread two wait until total amount of product feeds to be processed is declared by thread one
	private final Properties properties = new Properties();
	private final ObjectMapper mapper = new ObjectMapper();
	private ProductFeedsProcessHelper helper;
	private final Date currentDate = new Date();

	/**
	 * 
	 */
    public ProductFeedsPocessor() {
        super();
    }

    @Override
    public void init() {
    	try {
			this.properties.load(new FileInputStream(new File(this.getServletContext().getRealPath("/WEB-INF/productFeed.properties"))));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
    	this.helper = new ProductFeedsProcessHelper(properties.getProperty("crud.url"));
    }
    /**
     * 
     */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {	
        response.setStatus(HttpServletResponse.SC_ACCEPTED);

        //if we have an id it's because it was called manually, so we quickly process it
		if(request.getParameter("id")!=null)
		{
			String pfJson = RequestsCreator.createGetRequest(properties.getProperty("crud.url"), "ProductFeeds?action=getById&id="+request.getParameter("id"), null);
			Tblpf pf = this.mapper.readValue(pfJson, Tblpf.class);

			try {
				new Thread(new ProductFeedsRunnable(pf, helper, properties)).start();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else
		{
			doWork();
		}
	}

	/**
	 * 
	 */
	private void doWork()
	{
		//Map response to a List of Tblpf objects
		List<Tblpf> allProductFeedsList;
		try {
			final String productFeeds = RequestsCreator.createGetRequest(properties.getProperty("crud.url"), "ProductFeeds?action=getAll", null);
			allProductFeedsList = this.mapper.readValue(productFeeds, mapper.getTypeFactory().constructCollectionType(List.class, Tblpf.class));
		} catch (IOException e2) {
			e2.printStackTrace();
			return;
		}

		// aripe, logs management
		PFLogService pfLogService = new PFLogService();
		
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		final ExecutorService pfExecutor = Executors.newSingleThreadExecutor();
		for(Tblpf pf : allProductFeedsList)
		{
			// aripe, inserting log
			pfLogService.ProductFeedsPocessorBegin(pf.getPartnerId(), pf.getDescription().concat(" URL=").concat(pf.getDownloadURL()));
			
			executor.submit(new Runnable() {
				@Override
				public void run() {
						if(pf.getStartTime()!=null && pf.getStartTime().getTime() > Calendar.getInstance().getTimeInMillis())
							return;

						CronExpression expression;
						//Analyse cron, set as failed if there's a problem parsing it
						try {
							expression = new CronExpression(pf.getTimePeriod());
						} catch (ParseException e) {
							System.out.println("The cron expression for the product feed "+pf.getId()+" ("+pf.getDescription()+") doesn't seem to have a valid cron format");
							e.printStackTrace();
							helper.fail(pf.getId());
							return;
						}

						//get last standardisation timestamp and set it as a calendar to get the next execution time after it
						final Timestamp timestamp = pf.getLastStandardisation();
						if(timestamp==null)
							try {
								pfExecutor.submit(new ProductFeedsRunnable(pf, helper, properties));
							} catch (Exception e) {
								e.printStackTrace();
							}
						else
						{
							if(expression.getNextValidTimeAfter(new Date(timestamp.getTime())).getTime() <= currentDate.getTime() || pf.getStandardisationStatus().getName().equals(PfStatus.ERROR))
								try {
									pfExecutor.submit(new ProductFeedsRunnable(pf, helper, properties));
								} catch (Exception e) {
									e.printStackTrace();
								}
						}
						
							
				}
			});
			
		}

	}
}
