package winedunk.pf.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ejb.EJB;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import winedunk.pf.models.tblPartnersProducts;
import winedunk.pf.services.PartnersProductsService;

/**
 * Servlet implementation class Test
 */
@WebServlet("/Test")
public class Test extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private final Properties properties = new Properties();

	@EJB
	PartnersProductsService pp;

	public Test() {
        super();
    }

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
		//pp.getInstance(properties.getProperty("crud.url"));
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		List<tblPartnersProducts> products = pp.getAll();
		Long start = new Date().getTime();
		for(tblPartnersProducts product : products) {
			product.setDeleted(true);
		}
		
		Long end = new Date().getTime();
		System.out.println(end-start);

		start = new Date().getTime();
		Set<Integer> wineIds = products.parallelStream().filter(product -> !product.isDeleted()).map(product -> product.getTblWines().getId()).collect(Collectors.toSet());
		end = new Date().getTime();
		System.out.println(end-start);
		System.out.println(wineIds);
	}
}
