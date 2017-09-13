package winedunk.pf.services;

import java.util.Date;

import javax.ejb.LocalBean;
import javax.ejb.Stateful;
import javax.ejb.Stateless;

import winedunk.pf.models.Tblpf;
import winedunk.pf.models.Tblpfproduct;

/**
 * Session Bean implementation class TblPFImportingLogsService
 */
@Stateful
@LocalBean
public class TblPFImportingLogsService {

	/**
	 * Default constructor.
	 */
	public TblPFImportingLogsService() {
	}

	public void sendLog(String logMessage, String technicalDetails, Tblpf pf, Tblpfproduct product, Date date)
	{

	}
}
