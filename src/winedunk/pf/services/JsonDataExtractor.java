package winedunk.pf.services;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.helpers.Utils;
import winedunk.pf.models.Tblpfmerchanthtmlparsing;
import winedunk.pf.models.Tblpfproduct;

public final class JsonDataExtractor implements DataExtractor {

	private String token;

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	@Override
	public Map<String, String> parseWineData(String dataUrl, Map<String, String> dataRequestHeaders, Tblpfproduct product, List<Tblpfmerchanthtmlparsing> parsingInstructions) throws IOException {
		
		String contents = RequestsCreator.createGetRequest(Utils.getOrDefault(dataUrl, product.getProductURL()), dataRequestHeaders);

		JsonNode data = new ObjectMapper().readTree(contents);

		Map<String, String> wineValues = new HashMap<String, String>();
		for(Tblpfmerchanthtmlparsing parsingInstruction : parsingInstructions)
		{
			JsonNode field = data.findPath(parsingInstruction.getNameInWeb());
			if(field.isMissingNode())
				continue;
			
			String extractedValue = this.sanitise(parsingInstruction, field.asText());

			//Store values
			if(!StringUtils.isBlank(extractedValue))
				wineValues.put(parsingInstruction.getTblpfextractioncolumn().getColumnName(), extractedValue);
		}

		return wineValues;
	}
}
