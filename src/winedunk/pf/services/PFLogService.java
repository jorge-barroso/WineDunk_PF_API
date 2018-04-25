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

import winedunk.pf.models.tblPartners;

@Stateless
@LocalBean
public class PFLogService {
	
	@Context
	ServletContext context;

	Properties properties = new Properties();
	ObjectMapper mapper = new ObjectMapper();
	
	public void Init() {
		try {
			properties = new Properties();
			File thisClassFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getFile());
			String thisClassFilePath = thisClassFile.getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getAbsolutePath() + File.separator;
			properties.load(new FileInputStream(new File(thisClassFilePath + "productFeed.properties")));
			mapper.setSerializationInclusion(Include.NON_NULL);
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

	public void ProductFeedsPocessorBegin(tblPartners partner, String pfName) {
		
		if ( (partner != null) && (partner.getId() > 0) && (pfName != null) && (pfName != "") ) {

			Init();

			try {
				
				String encodedPFName = pfName.replace(" ", "%20");
				RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductfeedLog?action=productFeedsPocessorBegin&pfName=" + encodedPFName, mapper.writeValueAsString(partner), null);
				
			} catch (Exception e) {
				System.out.println("Exception at winedunk.pf.services.ProductFeedsPocessorBegin()");
				e.printStackTrace();
			}
		}
		
	}

	public void ProductFeedsPocessorEnd(tblPartners partner, String pfName) {
		
		if ( (partner != null) && (partner.getId() > 0) && (pfName != null) && (pfName != "") ) {

			Init();

			try {
				
				String encodedPFName = pfName.replace(" ", "%20");
				RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductfeedLog?action=productFeedsPocessorEnd&pfName=" + encodedPFName, mapper.writeValueAsString(partner), null);
				
			} catch (Exception e) {
				System.out.println("Exception at winedunk.pf.services.ProductFeedsPocessorEnd()");
				e.printStackTrace();
			}
		}
		
	}

	public void ProductsProcessorBegin(tblPartners partner) {
		
		if ( (partner != null) && (partner.getId() > 0) ) {

			Init();

			try {
				
				RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductfeedLog?action=productsProcessorBegin", mapper.writeValueAsString(partner), null);
				
			} catch (Exception e) {
				System.out.println("Exception at winedunk.pf.services.ProductsProcessorBegin()");
				e.printStackTrace();
			}
		}
		
	}

	public void ProductsProcessorEnd(tblPartners partner) {
		
		if ( (partner != null) && (partner.getId() > 0) ) {

			Init();

			try {
				
				RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductfeedLog?action=productsProcessorEnd", mapper.writeValueAsString(partner), null);
				
			} catch (Exception e) {
				System.out.println("Exception at winedunk.pf.services.ProductsProcessorEnd()");
				e.printStackTrace();
			}
		}
		
	}

	public void ProductStandardizingBegin(tblPartners partner, String partnerProductId) {
		
		if ( (partner != null) && (partner.getId() > 0) && (partnerProductId != null) && (partnerProductId != "") ) {

			Init();

			try {
				
				String encodedPartnerProductId = partnerProductId.replace(" ", "%20");
				RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductfeedLog?action=productStandardisationBegin&partnerProductId="+encodedPartnerProductId, mapper.writeValueAsString(partner), null);
				
			} catch (Exception e) {
				System.out.println("Exception at winedunk.pf.services.ProductStandardizingBegin()");
				e.printStackTrace();
			}
		}
		
	}

	public void ProductStandardizingEnd(tblPartners partner, String partnerProductId) {
		
		if ( (partner != null) && (partner.getId() > 0) && (partnerProductId != null) && (partnerProductId != "") ) {

			Init();

			try {
				
				String encodedPartnerProductId = partnerProductId.replace(" ", "%20");
				RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductfeedLog?action=productStandardisationEnd&partnerProductId="+encodedPartnerProductId, mapper.writeValueAsString(partner), null);
				
			} catch (Exception e) {
				System.out.println("Exception at winedunk.pf.services.ProductStandardizingEnd()");
				e.printStackTrace();
			}
		}
		
	}

	public void ProductProcessingBegin(tblPartners partner, String partnerProductId) {
		
		if ( (partner != null) && (partner.getId() > 0) && (partnerProductId != null) && (partnerProductId != "") ) {

			Init();

			try {
				
				String encodedPartnerProductId = partnerProductId.replace(" ", "%20");
				RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductfeedLog?action=productProcessingBegin&partnerProductId="+encodedPartnerProductId, mapper.writeValueAsString(partner), null);
				
			} catch (Exception e) {
				System.out.println("Exception at winedunk.pf.services.ProductProcessingBegin()");
				e.printStackTrace();
			}
		}
		
	}

	public void ProductProcessingEnd(tblPartners partner, String partnerProductId) {
		
		if ( (partner != null) && (partner.getId() > 0) && (partnerProductId != null) && (partnerProductId != "") ) {

			Init();

			try {
				
				String encodedPartnerProductId = partnerProductId.replace(" ", "%20");
				RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductfeedLog?action=productProcessingEnd&partnerProductId="+encodedPartnerProductId, mapper.writeValueAsString(partner), null);
				
			} catch (Exception e) {
				System.out.println("Exception at winedunk.pf.services.ProductProcessingEnd()");
				e.printStackTrace();
			}
		}
		
	}

	public void ProductProcessing(tblPartners partner, String logtypeName, String partnerProductId, String entityName, Integer entityId, String description) {
		
		if ( (partner != null) && (partner.getId() > 0) && (partnerProductId != null) && (partnerProductId != "") ) {

			Init();

			try {
				
				String encodedLogtypeName = logtypeName.replace(" ", "%20");
				String encodedPartnerProductId = partnerProductId.replace(" ", "%20");
				String encodedEntityName = entityName.replace(" ", "%20");
				String encodedDescription = description.replace(" ", "%20");
				RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductfeedLog?action=productProcessing&logtypeName="+encodedLogtypeName+"&partnerProductId="+encodedPartnerProductId+"&entityName="+encodedEntityName+"&entityId="+entityId+"&description="+encodedDescription, mapper.writeValueAsString(partner), null);
				
			} catch (Exception e) {
				System.out.println("Exception at winedunk.pf.services.ProductProcessing()");
				e.printStackTrace();
			}
		}
		
	}

	public void StoredprocedureCalled(tblPartners partner, String spName) {
		
		if ( (partner != null) && (partner.getId() > 0) && (spName != null) && (spName != "") ) {

			Init();

			try {
				
				spName = spName.replace(" ", "%20");
				RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductfeedLog?action=storedprocedureCalled&spName=" + spName, mapper.writeValueAsString(partner), null);
				
			} catch (Exception e) {
				System.out.println("Exception at winedunk.pf.services.StoredprocedureCalled()");
				e.printStackTrace();
			}
		}
		
	}
	
}