package winedunk.pf.controllers;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import winedunk.pf.models.viewUsers;
import winedunk.pf.services.ValidationService;
import winedunk.pf.services.LoginService;

/**
 * Servlet implementation class Login
 */
@WebServlet("/Login")
public class Login extends HttpServlet {
	private static final long serialVersionUID = 1L;

	Properties properties;
    public Login() throws FileNotFoundException, IOException {
        super();
    	properties = new Properties();
		properties.load(new FileInputStream("WEB-INF/productFeed.properties"));
    }

    //if the user is already logged in we just redirect him to the product feed manager, otherwise, we show the login form
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		//check if we remember the user
		ValidationService validationService = new ValidationService();
		validationService.setUrlPath(properties.getProperty("crud.url"));

		if(validationService.validateUser(request, response))
		{
			//if we do, check if is admin
			viewUsers user = (viewUsers) request.getSession().getAttribute("userLoggedIn");
			if(user.isAdmin())
			{
				response.sendRedirect("/");
				return;
			}
		}

		request.getRequestDispatcher("WEB-INF/templates/login.jsp").forward(request, response);
		return;
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		//if any required parameter is empty, we just redirect the user back to the login site 
		if(request.getAttribute("email").toString().isEmpty() || request.getAttribute("password").toString().isEmpty())
		{
			request.setAttribute("login", false);
			request.getRequestDispatcher("WEB-INF/templates/login.jsp").forward(request, response);
		}

		LoginService loginService = new LoginService();
		loginService.clearVariables(request);
		loginService.setEmail(request.getParameter("email"));
		loginService.setPassword(request.getParameter("password"));
		loginService.setUrlPath(properties.getProperty("crud.url"));

		//check if user ticked on the remember me checkbox
		String rememberMe = request.getParameter("chkRememberMe");
		if(rememberMe != null && rememberMe.equals("on"))
			loginService.setRememberMe(true);

		try
		{
			if(!loginService.LoginUser())  
			{ 
				request.getSession().setAttribute("isLoggedIn", false);
				request.getSession().setAttribute("signUp", false);
				request.setAttribute("emailEntered", request.getParameter("email"));
				request.getRequestDispatcher("WEB-INF/templates/login.jsp").forward(request, response);
			}
			else
			{
				if(loginService.getRememberMe() == true)
				{
					
					Cookie cookie = new Cookie("uvt", loginService.getUser().getLoginToken());
					Cookie messageCookie = new Cookie("nsu", "true");
					cookie.setMaxAge(60*60*24*90); //3 months
					messageCookie.setMaxAge(60*60*24*30); //1 month
					response.addCookie(cookie);
					response.addCookie(messageCookie);
				}
				
				request.getSession().setAttribute("userLoggedIn", loginService.getUser());
				request.getSession().setAttribute("isLoggedIn", true);
				doGet(request, response);
			}
		} catch (Exception e) { e.printStackTrace(); }
	}

}
