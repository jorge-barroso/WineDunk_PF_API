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
import java.util.Map;

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
	 * @param headers
	 * @return
	 * @throws IOException
	 */
	public static String createPostRequest(String urlPath, String relURL, String content, Map<String, String> headers) throws IOException
	{
		//Create request
		String fullURL = urlPath + relURL;
		System.out.println("RequestsCreator / createPostRequest ("+fullURL+")");

		return createPostRequest(fullURL, content, headers);
	}

	/**
	 * 
	 * @param urlString
	 * @param content
	 * @param headers
	 * @return
	 * @throws IOException
	 */
	public static String createPostRequest(String urlString, String content, Map<String, String> headers) throws IOException
	{
		URL url = new URL(urlString);
		HttpURLConnection con = url.getProtocol().equals("https") ? startHttpsConnection(url, true, content, headers) : startHttpConnection(url, true, content, headers);

		con = processRequestResult(con, url, headers);

		return readResponse(con);
	}

	/**
	 * 
	 * @param urlPath
	 * @return
	 * @throws IOException
	 */
	public static String createGetRequest(String urlPath, Map<String, String> headers) throws IOException
	{
		//Create request
		System.out.println("RequestsCreator / createGetRequest ("+urlPath+")");
		URL url = new URL(urlPath);
		HttpURLConnection con = url.getProtocol().equals("https") ? startHttpsConnection(url, false, null, headers) : startHttpConnection(url, false, null, headers);

		con = processRequestResult(con, url, headers);

		//Get result
		return readResponse(con);
	}

	/**
	 * 
	 * @param urlPath
	 * @param relURL
	 * @return
	 * @throws IOException
	 */
	public static String createGetRequest(String urlPath, String relURL, Map<String, String> headers) throws IOException
	{
		//Create request
		String fullURL = new String(urlPath + relURL);
		return createGetRequest(fullURL, headers);
	}


	/**
	 * 
	 * @param url
	 * @param isPostRequest
	 * @param content
	 * @return
	 * @throws IOException
	 */
	private static HttpURLConnection startHttpConnection(URL url, boolean isPostRequest, String content, Map<String, String> extraHeaders) throws IOException
	{
		HttpURLConnection con = (HttpURLConnection) url.openConnection();

		//add request header
		con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
		con.setRequestProperty("Accept-Language", "en-GB,en;q=0.5");
		con.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
		if(extraHeaders!=null)
		{
			//add extra custom headers
			for(Map.Entry<String, String> header : extraHeaders.entrySet())
			{
				con.setRequestProperty(header.getKey(), header.getValue());
			}
		}

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
	 * @param isPostRequest <strong>TRUE</strong> if it's a post request, <strong>FALSE</strong> otherwise
	 * @param content The body to use on a post request
	 * @return
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	private static HttpsURLConnection startHttpsConnection(URL url, boolean isPostRequest, String content, Map<String, String> extraHeaders) throws UnsupportedEncodingException, IOException
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
		con.setRequestProperty("Accept-Language", "en-GB,en;q=0.5");
		con.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
		if(extraHeaders!=null)
		{
			//add extra custom headers
			for(Map.Entry<String, String> header : extraHeaders.entrySet())
			{
				con.setRequestProperty(header.getKey(), header.getValue());
			}
		}

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

		con.connect();

		return con;
	}

	private static HttpURLConnection processRequestResult(HttpURLConnection con, URL url, Map<String, String> headers) throws MalformedURLException, UnsupportedEncodingException, IOException
	{
		try {
			while(con.getResponseCode()==HttpServletResponse.SC_MOVED_PERMANENTLY || con.getResponseCode()==HttpServletResponse.SC_MOVED_TEMPORARILY || con.getResponseCode()==HttpServletResponse.SC_SEE_OTHER)
			{
				url = new URL(con.getHeaderField("Location"));
				con = url.getProtocol().equals("https") ? startHttpsConnection(url, false, null, headers) : startHttpConnection(url, false, null, headers);
			}

			if ( (con.getResponseCode()/100!=2) && (con.getResponseCode() != 404) ) // we are ignoring this error because it usually happens when product page is not found at merchant site (nothing to do)
			{
				throw new IOException("Error "+con.getResponseCode()+": "+con.getResponseMessage());
			}
			
			return con;	
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 
	 * @param con
	 * @return
	 * @throws IOException
	 */
	private static String readResponse(URLConnection con) throws IOException {
		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream(), "UTF-8"));
		String inputLine;
		StringBuffer responseBuffer = new StringBuffer();

		while ((inputLine = in.readLine()) != null) { responseBuffer.append(inputLine); }

		in.close();

		return responseBuffer.toString();
	}
}
