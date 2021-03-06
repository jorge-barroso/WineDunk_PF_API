package winedunk.pf.services;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import winedunk.pf.helpers.HtmlExtractionTypes;
import winedunk.pf.helpers.TblWineFields;
import winedunk.pf.helpers.Utils;
import winedunk.pf.models.Tblpfmerchanthtmlparsing;
import winedunk.pf.models.Tblpfproduct;

public final class HtmlDataExtractor implements DataExtractor {

	private String token;

	public String getToken() {
		return token;
	}

	public void setToken(final String token) {
		this.token = token;
	}

	@Override
	public Map<String, String> parseWineData(final String dataUrl, final Map<String, String> dataRequestHeaders, final Tblpfproduct product, final List<Tblpfmerchanthtmlparsing> parsingInstructions) throws IOException {

		final String contents = RequestsCreator.createGetRequest(Utils.getOrDefault(dataUrl, product.getProductURL()), dataRequestHeaders);

		final Document html = Jsoup.parse(contents, "UTF-8");

		final Map<String, String> wineValues = new HashMap<String, String>((int)Math.ceil(parsingInstructions.size()/0.75));
    	try {
	    	//Extract all elements from html
    		final Elements elements = html.getAllElements();
	
	    	//for each element, look for keywords to find the remaining values of the wine
	    	for(int i=0;i<elements.size();i++)
	    	{
	    		for(final Tblpfmerchanthtmlparsing parsingInstruction : parsingInstructions)
	        	{
	    			if (parsingInstruction.getTblpfparsingextractionmethod()==null)
	    				continue;

	    			//if we have it already inserted on the values' map and it's not an empty value or a null value, we go for the next key
	    			boolean isBlank = StringUtils.isBlank(wineValues.get(parsingInstruction.getTblpfextractioncolumn().getColumnName()));
	    			if(!isBlank)
	    				continue;

	    			//if we don't find the current keyword go for the next one
	        		if(parsingInstruction.getMustMatch()!=null && parsingInstruction.getMustMatch())
	        		{
	        			if(!elements.get(i).ownText().equalsIgnoreCase(parsingInstruction.getNameInWeb()))
	        				continue;
	        		}
	        		else
	        		{
	        			if(!StringUtils.containsIgnoreCase(elements.get(i).ownText(), parsingInstruction.getNameInWeb()))
	        				continue;
	        		}

	        		//if it's not the tag we were looking for, skip it as well
	        		if(parsingInstruction.getHtmlTagType()!=null && !parsingInstruction.getHtmlTagType().trim().equals(elements.get(i).tagName().trim()))
	        			continue;

	        		//extract value
	        		String extractedValue;
	        		Element element;
	    			switch(parsingInstruction.getTblpfparsingextractionmethod().getMethod())
	    			{
	    				case HtmlExtractionTypes.SAME_TAG:
	    					element = elements.get(i);
	    					break;
	    				case HtmlExtractionTypes.CHILDREN:
	    					element = elements.get(i).child(0);
	    					break;
	    				case HtmlExtractionTypes.PLUS_TAG:
	    					element = elements.get(i + parsingInstruction.getNumberOfTags());
	    					break;
	    				case HtmlExtractionTypes.MINUS_TAG:
	    					element = elements.get(i - parsingInstruction.getNumberOfTags());
	    					break;
	    				case HtmlExtractionTypes.SPECIFIC_TAG:
	    					element = elements.get(i).getElementsByTag(parsingInstruction.getSpecificTag()).get(0);
	    					break;
						default:
							continue;
	    			}

	    			//If the value is an attribute get the attribute, otherwise just get the tag contents
					if(parsingInstruction.getAttribute()!=null)
						extractedValue = element.attr(parsingInstruction.getAttribute());
					else
						extractedValue = parsingInstruction.getTblpfparsingextractionmethod().getMethod().equals("SameTag") ? element.ownText().replace(parsingInstruction.getNameInWeb(), "").trim() : element.ownText().trim();

	    			if(parsingInstruction.getTblpfextractioncolumn().getReplaceRegularExpression()!=null)
	    				extractedValue = extractedValue.replaceAll(parsingInstruction.getTblpfextractioncolumn().getReplaceRegularExpression(), " ").trim();

	    			extractedValue = this.sanitise(parsingInstruction, extractedValue);

	    			//Add extra sanitise extracting the name of the country
	    			if(parsingInstruction.getTblpfextractioncolumn().getColumnName().equals(TblWineFields.REGION))
	    			{
	    				if(extractedValue.contains(","))
	    				{
	    					final String[] values = extractedValue.split("\\s*,\\s*");
	    					extractedValue = values[0];

	    					if(!wineValues.containsKey(TblWineFields.COUNTRY))
	    						wineValues.put(TblWineFields.COUNTRY, values[1]);
	    				}
	    			}

	    			if(parsingInstruction.getTblpfextractioncolumn().getColumnName().equals(TblWineFields.APPELLATION))
	    			//Store values
	    			if(!StringUtils.isBlank(extractedValue))
	    				wineValues.put(parsingInstruction.getTblpfextractioncolumn().getColumnName(), extractedValue);
	        	}
	    	}
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	return wineValues;
	}
}
