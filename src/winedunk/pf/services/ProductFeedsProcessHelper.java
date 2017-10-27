package winedunk.pf.services;

import java.io.IOException;

public class ProductFeedsProcessHelper {
	final RequestsCreator requestsCreator = new RequestsCreator();
	final String hostUrl;

	public ProductFeedsProcessHelper(String hostUrl) {
		this.hostUrl = hostUrl;
	}

	public void fail(Integer pfId)
	{
		try {
			this.requestsCreator.createGetRequest(this.hostUrl, "ProductFeeds?action=failStandardisation&id="+pfId);
		} catch (IOException e) {
			System.out.println("While sending a request to flag the product feed "+pfId+" as failed to the CRUD, this one wans't reachable");
			e.printStackTrace();
			return;
		}
	}

	public void ok(Integer pfId)
	{
		try {
			this.requestsCreator.createGetRequest(this.hostUrl, "ProductFeeds?action=okStandardisation&id="+pfId);
		} catch (IOException e) {
			System.out.println("While sending a request to flag the product feed "+pfId+" as failed to the CRUD, this one wans't reachable");
			e.printStackTrace();
			return;
		}
	}

	public void processing(Integer pfId)
	{
		try {
			this.requestsCreator.createGetRequest(this.hostUrl, "ProductFeeds?action=processingStandardisation&id="+pfId);
		} catch (IOException e) {
			System.out.println("While sending a request to flag the product feed "+pfId+" as failed to the CRUD, this one wans't reachable");
			e.printStackTrace();
			return;
		}
	}
}
