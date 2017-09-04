package winedunk.pf.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.services.RequestsCreator;

import winedunk.pf.models.Tblpfproduct;
import winedunk.pf.models.tblPartnersProducts;
import winedunk.pf.models.tblShops;
import winedunk.pf.models.Tblpfmerchanthtmlparsing;
import winedunk.pf.models.Tblpfparsingextractionmethod;

/**
 * Servlet implementation class ProductsProcessor
 */
@WebServlet(urlPatterns="/ProductsProcessor", asyncSupported=true)
public class ProductsProcessor extends HttpServlet {
	private static final long serialVersionUID = 1L;
	ExecutorService executor;
	final Properties properties;

	public ProductsProcessor() {
        super();
		properties = new Properties();
        executor = Executors.newFixedThreadPool(30);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setStatus(HttpServletResponse.SC_ACCEPTED);

		final AsyncContext async = request.getAsyncContext();
		properties.load(new FileInputStream(new File(this.getServletContext().getRealPath("/WEB-INF/productFeed.properties"))));

		async.start(new Runnable() {

			@Override
			public void run() {
				String parameters = request.getParameterMap().containsKey("pfId") ? "action=getByPfId&pfId="+request.getParameter("pfId") : "action=getAll";
				String productsJson;
				try {
					productsJson = new RequestsCreator().createPostRequest(properties.getProperty("crud.url"), "Products", parameters);
					List<Tblpfproduct> products = new ObjectMapper().readValue(productsJson, new TypeReference<List<Tblpfproduct>>(){});
					for(Tblpfproduct product : products)
					{
						executor.submit(new Runnable() {

							@Override
							public void run() {
								productProcess(product);
							}
							
						});
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		});
	}

    private void productProcess(Tblpfproduct product)
    {
    	Document htmlDoc;
    	List<Tblpfmerchanthtmlparsing> merchantParsings;
    	tblPartnersProducts partnersProducts;
    	try {
    		RequestsCreator requestsCreator = new RequestsCreator();
    		ObjectMapper mapper = new ObjectMapper();

    		String parameters = "partnerProductId="+product.getPartnerProductId()
    						  + "&merchanProductId="+product.getMerchantProductId();
    		String productString = requestsCreator.createPostRequest(properties.getProperty("crud.url"), "getByPartnerProductIdAndMerchantProductId?action=getByPartnerProductIdAndMerchantProductId", parameters);
    		partnersProducts = productString.isEmpty() ? new tblPartnersProducts() : mapper.readValue(productString, tblPartnersProducts.class);
			//TODO crud controller and service
    		//get merchant
			tblShops merchant = mapper.readValue(requestsCreator.createPostRequest(properties.getProperty("crud.url"), "Merchants", "name="+product.getMerchantName()), tblShops.class);
			if(merchant==null)
	    		return;

			//get parsing instructions by the merchant
			String merchantString = requestsCreator.createPostRequest(properties.getProperty("crud.url"), "TblPfMerchantsHTMLParsing" , "action=getByMerchant&id="+merchant.getId());
			merchantParsings = mapper.readValue(merchantString, new TypeReference<List<Tblpfmerchanthtmlparsing>>(){});
			
			//download html
    		htmlDoc = Jsoup.parse(requestsCreator.createGetRequest(product.getProductURL()));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

    	Elements elements = htmlDoc.getAllElements();
    	for(int i=0;i<elements.size();i++)
    	{
    		for(Tblpfmerchanthtmlparsing merchantParsing : merchantParsings)
        	{
        		if(elements.get(i).ownText()==merchantParsing.getNameInWeb())
        		{
        			Tblpfparsingextractionmethod extractionMethod = merchantParsing.getTblpfparsingextractionmethod();
        			String extractedValue;
        			//extract value
        			switch(extractionMethod.getMethod())
        			{
        				case "SameTag":
        					extractedValue = elements.get(i).ownText().replace(merchantParsing.getNameInWeb(), "");
        					break;
        				case "Children":
        					extractedValue = elements.get(i).child(0).ownText();
        				case "NextTag":
        					extractedValue = elements.get(++i).ownText();
        			}
        			
        			switch(merchantParsing.getTblpfextractioncolumn().getColumnName())
        			{
        				//TODO apply checks and filters to the extracted value
        				case "Closure":
        					break;
        				case "Vintage":
        					break;
        				case "ImageUrl":
        					break;
        				case "":
        					break;
        			}
        		}
        	}
    	}
    }
}