package winedunk.pf.services;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class RequestsCreator extends EncodeURL {
	
	public String createPostRequest(String urlPath, String relURL, String content) throws IOException
	{
		//Create request
		String fullURL = urlPath + relURL;
		fullURL.replace(" ", "+");
		URL url = new URL(fullURL);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();

		//add request header
		con.setRequestMethod("POST");
		con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
		con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
		con.setRequestProperty("Content-Type", "application/json;charset=UTF-8");


		// Send post request
	    con.setUseCaches(false);
		con.setDoOutput(true);

	    DataOutputStream os = new DataOutputStream(con.getOutputStream());
	    os.write(content.getBytes("UTF-8"));
	    os.close();
		
		
		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer responseBuffer = new StringBuffer();

		while ((inputLine = in.readLine()) != null) { responseBuffer.append(inputLine); }

		in.close();
		
		return responseBuffer.toString();
	}

	public String createGetRequest(String url) throws IOException
	{
		//Create request
		url = url.replaceAll(" ", "+");
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
				
		//add request header
		con.setRequestMethod("GET");
		con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
		con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
		con.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
		
		//Get result
		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer responseBuffer = new StringBuffer();

		while ((inputLine = in.readLine()) != null) { responseBuffer.append(inputLine); }
		in.close();
		return responseBuffer.toString();
	}

	public String createGetRequest(String urlPath, String relURL) throws IOException
	{
		//Create request
		String fullURL = new String(urlPath + relURL);
		return this.createGetRequest(fullURL);
	}
}
