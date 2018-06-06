package winedunk.pf.runnables;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.winedunk.fileutils.Zip;

import winedunk.pf.models.Tblpf;
import winedunk.pf.models.Tblpfmapping;
import winedunk.pf.models.Tblpfproduct;
import winedunk.pf.services.PFLogService;
import winedunk.pf.services.ProductFeedsProcessHelper;
import winedunk.pf.services.RequestsCreator;

public class ProductFeedsRunnable implements Runnable {

	private final Tblpf pf;
	private final ProductFeedsProcessHelper helper;
	private final Properties properties;
	private final ObjectMapper mapper = new ObjectMapper();
	
	public ProductFeedsRunnable(Tblpf pf, ProductFeedsProcessHelper helper, Properties properties) throws Exception {
			super();
			this.pf = pf;
			this.helper = helper;
			this.properties = properties;
	}

	@Override
	public void run() {
		try {
			this.processProductFeed();
		} catch (Exception e) {
			e.printStackTrace();
			this.helper.fail(this.pf.getId());
		} finally {
			new File("productFeed"+pf.getId()).delete();
		}
	}

	/**
	 * 
	 * @param id
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public void processProductFeed() throws MalformedURLException, IOException, Exception
	{
		
		// aripe, logs management
		PFLogService pfLogService = new PFLogService();
		
		this.helper.processing(this.pf.getId());

		final String pfMappingJson = RequestsCreator.createGetRequest(properties.getProperty("crud.url"), "PFMapping?action=getByPFId&id="+this.pf.getId(), null);
		final Tblpfmapping pfMapping = this.mapper.readValue(pfMappingJson, Tblpfmapping.class);  

		final String pfName = "productFeed"+this.pf.getId();
		final URL feedURL = new URL(this.pf.getDownloadURL());
		try {
			if(this.pf.getIsZip())
			{
				final String zipName = "this.pf.zip"+this.pf.getId();
				try (final ReadableByteChannel channel = Channels.newChannel(feedURL.openStream());
					final FileOutputStream outputFile = new FileOutputStream(zipName)) 
				{
					outputFile.getChannel().transferFrom(channel, 0, Long.MAX_VALUE);
				}
	
				final Zip zip = new Zip();
				zip.unzip(zipName, pfName);
				new File(zipName).delete();
			}
			else
			{
				try (final ReadableByteChannel channel = Channels.newChannel(feedURL.openStream());
						final FileOutputStream outputFile = new FileOutputStream(pfName)) 
					{
						outputFile.getChannel().transferFrom(channel, 0, Long.MAX_VALUE);
					}
			}
		} catch(Exception e) {
			e.printStackTrace();
			this.helper.fail(this.pf.getId());
		}

		//get a list of files
		final File[] files = new File(pfName).isDirectory() ? new File(pfName).listFiles() : new File[] {new File(pfName)};

		for(File file : files)
		{
			try {
				final char separator = this.pf.getSeparator().equals("\\t") ? '\t' : this.pf.getSeparator().charAt(0);
				final CSVParser parser = new CSVParserBuilder().withSeparator(separator).build();
	
				try(final CSVReader reader = new CSVReaderBuilder(new FileReader(file)).withSkipLines(this.pf.getHasHeader() ? 1 : 0).withCSVParser(parser).build())
				{
					//in this list we will store the IDs of the products we process (to set the old products as deleted)
					final List<Integer> productsFound = Collections.synchronizedList(new ArrayList<Integer>());

					final ExecutorService processExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()-1);

					String[] lineValues;
					final String crudUrl = properties.getProperty("crud.url");
					
					while((lineValues = reader.readNext())!= null)
					{
						final String[] finalValues = lineValues;
						processExecutor.execute(new Runnable() {

							@Override
							public void run() {
								
								Tblpfproduct product;
								
								try {
									if (Boolean.parseBoolean(RequestsCreator.createGetRequest(crudUrl+"PFProductsBlacklist?partnerId="+pf.getPartnerId().getId()+"&partnerProductId="+finalValues[pfMapping.getPartnerProductIdColumn()], null))) {
										return;
									} else {
										// aripe 2018-03-31
										String parameters = "{ \"partnerId\"  : \"" + pf.getPartnerId().getId() + "\", "
														  + "  \"partnerProductId\" : \"" + finalValues[pfMapping.getPartnerProductIdColumn()] + "\"}";
										product = mapper.readValue(RequestsCreator.createPostRequest(crudUrl+"Products?action=findByPartnerIdAndPartnerProductId", parameters, null), Tblpfproduct.class);

									}
								} catch (JsonParseException e) {
									System.out.println("Exception: While trying to get the possibly existing product from the CRUD before updating/inserting it, the response provided by this one doesn't seem to have a proper JSON format");
									e.printStackTrace();
									helper.fail(pf.getId());
									return;
								} catch (JsonMappingException e) {
									System.out.println("Exception: While trying to get the possibly existing product from the CRUD before updating/inserting it, the response provided by this one couldn't be mapped to a Tblpfproduct object");
									e.printStackTrace();
									helper.fail(pf.getId());
									return;
								} catch (IOException e) {
									System.out.println("Exception: While trying to get the possibly existing product from the CRUD before updating/inserting it, couldn't reach the CRUD or a low-level I/O exception occurred, please check the server");
									e.printStackTrace();
									helper.fail(pf.getId());
									return;
								}

								//populate values from the file using the mapping
								product.setClicktag(finalValues[pfMapping.getClicktagColumn()]);
								product.setImageURL(finalValues[pfMapping.getImageURLColumn()]);
								product.setMerchantName(StringEscapeUtils.unescapeHtml4(finalValues[pfMapping.getMerchantNameColumn()]));
								product.setMerchantProductId(finalValues[pfMapping.getMerchantProductIdColumn()]);
								product.setName(finalValues[pfMapping.getNameColumn()]);
								product.setPartnerProductDescription(finalValues[pfMapping.getPartnerProductDescriptionColumn()]);
								product.setPartnerProductId(finalValues[pfMapping.getPartnerProductIdColumn()]);
								product.setProductURL(finalValues[pfMapping.getProductURLColumn()]);
								product.setTblpf(pf);

								if(!StringUtils.isBlank(finalValues[pfMapping.getDeliveryCostColumn()]))
									product.setDeliveryCost(Float.valueOf(finalValues[pfMapping.getDeliveryCostColumn()]));
								if(pfMapping.getPartnerMerchantId()!=null)
									product.setPartnerMerchantId(finalValues[pfMapping.getPartnerMerchantId()]);
								if(!StringUtils.isBlank(finalValues[pfMapping.getPriceColumn()]))
									product.setPrice(Float.valueOf(finalValues[pfMapping.getPriceColumn()]));

								//Extract product type if needed or just assign
								String productType = finalValues[pfMapping.getWineTypeColumn()];
								if(productType.contains("->"))
									productType = productType.replaceAll(".*\\s*->\\s*", "");

								product.setProductType(productType);

								//persist only if it's a new product
								if(product.getId()==null)
								{
									try {
										
										product.setId(Integer.parseInt(RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "Products?action=addProduct", mapper.writeValueAsString(product), null)));
									} catch (NumberFormatException e) {
										System.out.println("Exception: After adding the product to the database, the id returned doesn't seem to be a valid number");
										e.printStackTrace();
										helper.fail(pf.getId());
										return;
									} catch (JsonProcessingException e) {
										System.out.println("Exception: Whiles trying to add a new product to the database, there was a problem while serialising it");
										e.printStackTrace();
										helper.fail(pf.getId());
										return;
									} catch (IOException e) {
										System.out.println("Exception: Whiles trying to add a new product to the database, the CRUD wasn't reachable");
										e.printStackTrace();
										helper.fail(pf.getId());
										return;
									}
								}
								else
								{
									try {
										final Boolean updated = Boolean.parseBoolean(RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "Products?action=updateProduct", mapper.writeValueAsString(product), null));
										if(!updated)
											System.out.println("Exception: Something went wrong updating the product on `tblPFProducts`");
									} catch (JsonProcessingException e) {
										System.out.println("Exception: Whiles trying to update a product on `tblPFProducts`, there was a problem while serialising it");
										e.printStackTrace();
										helper.fail(pf.getId());
										return;
									} catch (IOException e) {
										System.out.println("Exception: Whiles trying to update a product on `tblPFProducts`, the CRUD wasn't reachable");
										e.printStackTrace();
										helper.fail(pf.getId());
										return;
									}
								}
		
								productsFound.add(product.getId());
								

								// aripe, inserting log
								pfLogService.ProductStandardizingEnd(product.getTblpf().getPartnerId(), product.getPartnerProductId());
								
								System.out.println("Finished processing product number "+product.getId());
							}
		
						});
					}

					processExecutor.shutdown();
					try {
						processExecutor.awaitTermination(1l, TimeUnit.DAYS);
					} catch (InterruptedException e) {
						e.printStackTrace();
						helper.fail(this.pf.getId());
						//if the tread is interrupted we just return without updating the last importation date, as the thread failed
						return;
					}
					
					//Get all the listed products from this parter and remove those which id is not contaied in the list with the current products' ids
					final String productsListJson = RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "Products?action=getByPfId", "{\"id\" : "+this.pf.getId()+"}", null);
					final List<Tblpfproduct> productsList = this.mapper.readValue(productsListJson, new TypeReference<List<Tblpfproduct>>(){});
					for(Tblpfproduct pfProduct : productsList)
					{
						if(!productsFound.contains(pfProduct.getId()))
						{
							if(!Boolean.parseBoolean(RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "Products?action=deleteProduct", "{\"id\" : "+pfProduct.getId()+"}", null)))
								System.out.println("Exception: Couldn't delete product "+pfProduct.getId());
						}
					}

					//set status as correctly imported
					this.pf.setLastStandardisation(new Timestamp(new Date().getTime()));
					RequestsCreator.createPostRequest(properties.getProperty("crud.url"), "ProductFeeds?action=update", this.mapper.writeValueAsString(pf), null);
					this.helper.ok(this.pf.getId());

					// aripe, Inserting Log
					pfLogService.ProductFeedsPocessorEnd(pf.getPartnerId(), pf.getDescription().concat(" URL=").concat(pf.getDownloadURL()));

				}
			} finally {
				file.delete();
			}
		}
	}
}
