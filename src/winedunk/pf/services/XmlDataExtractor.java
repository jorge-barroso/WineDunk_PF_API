package winedunk.pf.services;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import winedunk.pf.models.Tblpfmerchanthtmlparsing;
import winedunk.pf.models.Tblpfproduct;

public class XmlDataExtractor implements DataExtractor {

	private String token;

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	@Override
	public Map<String, String> parseWineData(String dataUrl, Map<String, String> dataRequestHeaders,
			Tblpfproduct product, List<Tblpfmerchanthtmlparsing> parsingInstructions) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
