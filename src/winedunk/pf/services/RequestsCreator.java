package winedunk.pf.services;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.http.HttpServletResponse;


public class RequestsCreator extends EncodeURL {

	/**
	 * 
	 * @param urlPath
	 * @param relURL
	 * @param content
	 * @return
	 * @throws IOException
	 */
	public String createPostRequest(String urlPath, String relURL, String content) throws IOException
	{
		//Create request
		String fullURL = urlPath + relURL;
		System.out.println("Full URL: "+fullURL);
		URL url = new URL(fullURL);
		HttpURLConnection con = url.getProtocol().equals("https") ? this.startHttpsConnection(url, true, content) : this.startHttpConnection(url, true, content);

		con = this.processRequestResult(con, url);

		return this.readResponse(con);
	}

	/**
	 * 
	 * @param urlPath
	 * @return
	 * @throws IOException
	 */
	public String createGetRequest(String urlPath) throws IOException
	{
		//Create request
		System.out.println("Full URL: "+urlPath);
		URL url = new URL(urlPath);
		HttpURLConnection con = url.getProtocol().equals("https") ? this.startHttpsConnection(url, false, null) : this.startHttpConnection(url, false, null);

		con = this.processRequestResult(con, url);

		//Get result
		return this.readResponse(con);
	}

	/**
	 * 
	 * @param urlPath
	 * @param relURL
	 * @return
	 * @throws IOException
	 */
	public String createGetRequest(String urlPath, String relURL) throws IOException
	{
		//Create request
		String fullURL = new String(urlPath + relURL);
		return this.createGetRequest(fullURL);
	}


	/**
	 * 
	 * @param url
	 * @param isPostRequest
	 * @param content
	 * @return
	 * @throws IOException
	 */
	private HttpURLConnection startHttpConnection(URL url, boolean isPostRequest, String content) throws IOException
	{
		HttpURLConnection con = (HttpURLConnection) url.openConnection();

		//add request header
		con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
		con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
		con.setRequestProperty("Content-Type", "application/json;charset=UTF-8");

		// Send post request
		if(isPostRequest)
		{
			con.setRequestMethod("POST");
			con.setUseCaches(false);
			con.setDoOutput(true);

			DataOutputStream os = new DataOutputStream(con.getOutputStream());
		    os.write(content.getBytes("UTF-8"));
		    os.close();
		}
		else
		{
			con.setRequestMethod("GET");
		}

		//if there's a redirection, start again this chunk of code with the new url extracted from the location header
		con.connect();

		return con;
	}

	/**
	 * 
	 * @param url
	 * @param isPostRequest
	 * @param content
	 * @return
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	private HttpsURLConnection startHttpsConnection(URL url, boolean isPostRequest, String content) throws UnsupportedEncodingException, IOException
	{
		TrustManager[] trustAllManager = new TrustManager[] {
			new X509TrustManager()
			{
				@Override
				public X509Certificate[] getAcceptedIssuers() { return null; }

				@Override
				public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}

				@Override
				public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
			} 
		};

		SSLContext context;
		try {
			context = SSLContext.getInstance("SSL");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
		try {
			context.init(null, trustAllManager, new SecureRandom());
		} catch (KeyManagementException e) {
			e.printStackTrace();
			return null;
		}

		HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());

		HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

		//add request header
		con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
		con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
		con.setRequestProperty("Content-Type", "application/json;charset=UTF-8");

		// Send post request
		if(isPostRequest)
		{
			con.setRequestMethod("POST");
			con.setUseCaches(false);
			con.setDoOutput(true);

			DataOutputStream os = new DataOutputStream(con.getOutputStream());
		    os.write(content.getBytes("UTF-8"));
		    os.close();
		}
		else
		{
			con.setRequestMethod("GET");
		}

		con.connect();

		return con;
	}

	private HttpURLConnection processRequestResult(HttpURLConnection con, URL url) throws MalformedURLException, UnsupportedEncodingException, IOException
	{
		while(con.getResponseCode()==HttpServletResponse.SC_MOVED_PERMANENTLY || con.getResponseCode()==HttpServletResponse.SC_MOVED_TEMPORARILY || con.getResponseCode()==HttpServletResponse.SC_SEE_OTHER)
		{
			url = new URL(con.getHeaderField("Location"));
			con = url.getProtocol().equals("https") ? this.startHttpsConnection(url, false, null) : this.startHttpConnection(url, false, null);
		}

		if(con.getResponseCode()/100!=2)
		{
			throw new IOException("Error "+con.getResponseCode()+": "+con.getResponseMessage());
		}
		
		return con;
	}

	/**
	 * 
	 * @param con
	 * @return
	 * @throws IOException
	 */
	private String readResponse(URLConnection con) throws IOException {
		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream(), "UTF-8"));
		String inputLine;
		StringBuffer responseBuffer = new StringBuffer();

		while ((inputLine = in.readLine()) != null) { responseBuffer.append(inputLine); }

		in.close();
		
		return responseBuffer.toString();
	}
}
