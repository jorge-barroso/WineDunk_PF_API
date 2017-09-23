package winedunk.pf.models;

import java.io.Serializable;
import javax.persistence.*;
import java.util.List;


/**
 * The persistent class for the tblpfextractioncolumns database table.
 * 
 */
@Entity
@Table(name="tblPFExtractionColumns")
@NamedQuery(name="Tblpfextractioncolumn.findAll", query="SELECT t FROM Tblpfextractioncolumn t")
public class Tblpfextractioncolumn implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private int id;

	private String columnName;

	//bi-directional many-to-one association to Tblpfmerchanthtmlparsing
	@OneToMany(mappedBy="tblpfextractioncolumn")
	private List<Tblpfmerchanthtmlparsing> tblpfmerchanthtmlparsings;

	public Tblpfextractioncolumn() {
	}

	public int getId() {
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getColumnName() {
		return this.columnName;
	}

	public void setColumnName(String columnName) {
		this.columnName = columnName;
	}

	public List<Tblpfmerchanthtmlparsing> getTblpfmerchanthtmlparsings() {
		return this.tblpfmerchanthtmlparsings;
	}

	public void setTblpfmerchanthtmlparsings(List<Tblpfmerchanthtmlparsing> tblpfmerchanthtmlparsings) {
		this.tblpfmerchanthtmlparsings = tblpfmerchanthtmlparsings;
	}

	public Tblpfmerchanthtmlparsing addTblpfmerchanthtmlparsing(Tblpfmerchanthtmlparsing tblpfmerchanthtmlparsing) {
		getTblpfmerchanthtmlparsings().add(tblpfmerchanthtmlparsing);
		tblpfmerchanthtmlparsing.setTblpfextractioncolumn(this);

		return tblpfmerchanthtmlparsing;
	}

	public Tblpfmerchanthtmlparsing removeTblpfmerchanthtmlparsing(Tblpfmerchanthtmlparsing tblpfmerchanthtmlparsing) {
		getTblpfmerchanthtmlparsings().remove(tblpfmerchanthtmlparsing);
		tblpfmerchanthtmlparsing.setTblpfextractioncolumn(null);

		return tblpfmerchanthtmlparsing;
	}

}