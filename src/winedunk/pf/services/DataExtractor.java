package winedunk.pf.services;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.WordUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CharMatcher;

import winedunk.pf.helpers.TblWineFields;
import winedunk.pf.models.Tblpfmerchanthtmlparsing;
import winedunk.pf.models.Tblpfproduct;

public interface DataExtractor {

	/**
	 * Function to produce a login 
	 * @param loginUrl
	 * @param tokenField
	 * @return
	 * @throws IOException
	 */
	default String login(String loginUrl, String tokenField) throws IOException
	{
		if(loginUrl==null)
			return null;

		String response = RequestsCreator.createGetRequest(loginUrl, null);

		return tokenField!=null ? new ObjectMapper().readTree(response).findPath(tokenField).asText() : response;
		
		
	}

	/**
	 * 
	 * @param parsingInstruction
	 * @param extractedValue
	 * @return
	 */
	default String sanitise(Tblpfmerchanthtmlparsing parsingInstruction, String extractedValue)
	{
		//Sanitise and clean values
		if(parsingInstruction.getTblpfextractioncolumn().getColumnName().equals(TblWineFields.BOTTLE_SIZE))
		{
			String sanitizedValue = CharMatcher.digit().or(CharMatcher.is('.')).retainFrom(extractedValue);

			if(!NumberUtils.isCreatable(sanitizedValue))
				return null;

			Float temporaryValue = Float.parseFloat(sanitizedValue);

			if(StringUtils.containsIgnoreCase(extractedValue, "m"))
				extractedValue = String.valueOf(temporaryValue/10);
			else if(StringUtils.containsIgnoreCase(extractedValue, "d"))
				extractedValue = String.valueOf(temporaryValue*10);
			else if(StringUtils.containsIgnoreCase(extractedValue, "l") && !StringUtils.containsIgnoreCase(extractedValue, "c"))
				extractedValue = String.valueOf(temporaryValue*100);
			else
				extractedValue = String.valueOf(temporaryValue);
		}
		else if(parsingInstruction.getTblpfextractioncolumn().getColumnName().equals(TblWineFields.ABV))
		{
			extractedValue = CharMatcher.digit().or(CharMatcher.is('.')).retainFrom(extractedValue);
		}
		else if(parsingInstruction.getTblpfextractioncolumn().getColumnName().equals(TblWineFields.COUNTRY))
		{
			extractedValue = WordUtils.capitalizeFully(extractedValue);
		}
		else if(parsingInstruction.getTblpfextractioncolumn().getColumnName().equals(TblWineFields.VINTAGE))
		{
			String temporaryValue = CharMatcher.digit().retainFrom(extractedValue);
			extractedValue = temporaryValue.isEmpty() ? null : temporaryValue;
		}

		return extractedValue;
	}

	/**
	 * 
	 * @param dataUrl
	 * @param dataRequestHeaders
	 * @param product
	 * @param parsingInstructions
	 * @return
	 * @throws IOException
	 */
	Map<String, String> parseWineData(String dataUrl, Map<String, String> dataRequestHeaders, Tblpfproduct product, List<Tblpfmerchanthtmlparsing> parsingInstructions) throws IOException;

}
