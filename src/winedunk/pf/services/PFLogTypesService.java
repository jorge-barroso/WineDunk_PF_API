package winedunk.pf.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.servlet.ServletContext;
import javax.ws.rs.core.Context;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

import winedunk.pf.models.TblPFLogTypes;


@Stateless
@LocalBean
public class PFLogTypesService {
	
	@Context
	ServletContext context;

	Properties properties = new Properties();
	ObjectMapper mapper = new ObjectMapper();
	String apiUrl;
	
	public void Init() {
		try {
			properties = new Properties();
			File thisClassFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getFile());
			String thisClassFilePath = thisClassFile.getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getAbsolutePath() + File.separator;
			properties.load(new FileInputStream(new File(thisClassFilePath + "productFeed.properties")));
			mapper.setSerializationInclusion(Include.NON_NULL);
			apiUrl = properties.getProperty("crud.url");
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}
		
	}
    
    public TblPFLogTypes getLogTypeError() {	
    	
    	this.Init();
    	
    	TblPFLogTypes pfLogTypes = new TblPFLogTypes();
    	String pfLogTypesJson;
		try {
			pfLogTypesJson = RequestsCreator.createGetRequest(apiUrl, "ProductfeedLog?action=getLogTypeError", null);
			pfLogTypes = mapper.readValue(pfLogTypesJson, TblPFLogTypes.class);
		} catch (IOException e) {
			pfLogTypes = null;
			e.printStackTrace();
		}
		return pfLogTypes;
    }
    
    public TblPFLogTypes getLogTypeWarning() {		
    	
    	this.Init();
    	
    	TblPFLogTypes pfLogTypes = new TblPFLogTypes();
    	String pfLogTypesJson;
		try {
			pfLogTypesJson = RequestsCreator.createGetRequest(apiUrl, "ProductfeedLog?action=getLogTypeWarning", null);
			pfLogTypes = mapper.readValue(pfLogTypesJson, TblPFLogTypes.class);
		} catch (IOException e) {
			pfLogTypes = null;
			e.printStackTrace();
		}
		return pfLogTypes;
    }
    
    public TblPFLogTypes getLogTypeInformation() {		
    	
    	this.Init();
    	
    	TblPFLogTypes pfLogTypes = new TblPFLogTypes();
    	String pfLogTypesJson;
		try {
			pfLogTypesJson = RequestsCreator.createGetRequest(apiUrl, "ProductfeedLog?action=getLogTypeInformation", null);
			pfLogTypes = mapper.readValue(pfLogTypesJson, TblPFLogTypes.class);
		} catch (IOException e) {
			pfLogTypes = null;
			e.printStackTrace();
		}
		return pfLogTypes;
    }

}
