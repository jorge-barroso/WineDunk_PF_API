package winedunk.pf.models;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Table(name = "tblCountries")
@NamedQuery(name = "tblCountries.findByName", query = "SELECT t FROM tblCountries t WHERE t.name = :name")
public class tblCountries {

	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "id")
	private Integer id;

	@Column(name = "name")
	private String name;

	@Column(name = "`isoAlpha-2-Code`")
	private String isoAlpha2Code;

	@Column(name = "`isoAlpha-3-Code`")
	private String isoAlpha3Code;

	@Column(name = "isoNumericCode")
	private Integer isoNumericCode;

	@Column(name = "deleted")
	private Boolean deleted;

	public Boolean isDeleted() {
		return deleted;
	}

	@ManyToMany
	@JoinTable(name = "tblCountries_Currencies", joinColumns = @JoinColumn(name = "countryId"), inverseJoinColumns = @JoinColumn(name = "currencyId"))
	private List<tblCurrencies> currencies;

	@ManyToMany
	@JoinTable(name = "tblCountries_Languages", joinColumns = @JoinColumn(name = "countryId"), inverseJoinColumns = @JoinColumn(name = "languageId"))
	private List<tblLanguages> languages;

	@ManyToMany
	@JoinTable(name = "tblCountries_TimeZones", joinColumns = @JoinColumn(name = "countryId"), inverseJoinColumns = @JoinColumn(name = "timeZoneId"))
	private List<tblTimeZones> timeZones;

	@OneToMany(mappedBy = "countryId", targetEntity = tblUsers.class)
	@JsonBackReference("user_country")
	private List<tblUsers> users;

	@OneToMany(mappedBy = "tblCountries", targetEntity = tblRegions.class)
	@JsonBackReference("region_country")
	private List<tblRegions> regions;

	@OneToMany(mappedBy = "tblCountries", targetEntity = TblPFCountryNameMappingTable.class)
	@JsonBackReference("TblPFCountryNameMappingTable_country")
	private List<TblPFCountryNameMappingTable> tblPFCountryNameMappingTables;

	@OneToMany(mappedBy = "countryId", targetEntity = tblCountriesWithWines.class)
	@JsonBackReference("countryWithWine")
	private List<tblCountriesWithWines> countriesWithWines;

	@OneToMany(mappedBy = "tblCountry", targetEntity = tblWineries.class)
	@JsonBackReference("winery_coutry")
	private List<tblWineries> tblWineries;

	@OneToMany(mappedBy = "countryId", targetEntity = tblAppellations.class)
	@JsonBackReference("appellation_country")
	private List<tblAppellations> appellations;

	public tblCountries(Integer id) {
		this.id = id;
	}

	public tblCountries() {
		this.id = null;
		this.name = null;
		this.isoAlpha2Code = null;
		this.isoAlpha3Code = null;
		this.isoNumericCode = null;
		this.deleted = null;
		this.currencies = null;
		this.languages = null;
		this.users = null;
		this.timeZones = null;
		this.regions = null;
		this.countriesWithWines = null;
		this.appellations = null;

	}

	public tblCountries(String name) {
		this.name = name;
	}

	public Integer getId() {
		return id;
	}

	public tblCountries setId(Integer id) {
		this.id = id;
		return this;
	}

	public String getName() {
		return name;
	}

	public tblCountries setName(String name) {
		this.name = name;
		return this;
	}

	public String getIsoAlpha2Code() {
		return isoAlpha2Code;
	}

	public tblCountries setIsoAlpha2Code(String isoAlpha2Code) {
		this.isoAlpha2Code = isoAlpha2Code;
		return this;
	}

	public String getIsoAlpha3Code() {
		return isoAlpha3Code;
	}

	public tblCountries setIsoAlpha3Code(String isoAlpha3Code) {
		this.isoAlpha3Code = isoAlpha3Code;
		return this;
	}

	public Integer getIsoNumericCode() {
		return isoNumericCode;
	}

	public tblCountries setIsoNumericCode(Integer isoNumericCode) {
		this.isoNumericCode = isoNumericCode;
		return this;
	}

	public Boolean getDeleted() {
		return deleted;
	}

	public tblCountries setDeleted(Boolean deleted) {
		this.deleted = deleted;
		return this;
	}

	public List<tblCurrencies> getCurrencies() {
		return currencies;
	}

	public tblCountries setCurrencies(List<tblCurrencies> currencies) {
		this.currencies = currencies;
		return this;
	}

	public List<tblLanguages> getLanguages() {
		return languages;
	}

	public tblCountries setLanguages(List<tblLanguages> languages) {
		this.languages = languages;
		return this;
	}

	public List<tblTimeZones> getTimeZones() {
		return timeZones;
	}

	public tblCountries setTimeZones(List<tblTimeZones> timeZones) {
		this.timeZones = timeZones;
		return this;
	}

	public List<tblUsers> getUsers() {
		return users;
	}

	public tblCountries setUsers(List<tblUsers> users) {
		this.users = users;
		return this;
	}

	public List<tblRegions> getRegions() {
		return regions;
	}

	public tblCountries setRegions(List<tblRegions> regions) {
		this.regions = regions;
		return this;
	}

	public List<TblPFCountryNameMappingTable> getTblPFCountryNameMappingTables() {
		return tblPFCountryNameMappingTables;
	}

	public tblCountries setTblPFCountryNameMappingTables(List<TblPFCountryNameMappingTable> tblPFCountryNameMappingTables) {
		this.tblPFCountryNameMappingTables = tblPFCountryNameMappingTables;
		return this;
	}

	public List<tblCountriesWithWines> getCountriesWithWines() {
		return countriesWithWines;
	}

	public tblCountries setCountriesWithWines(List<tblCountriesWithWines> countriesWithWines) {
		this.countriesWithWines = countriesWithWines;
		return this;
	}

	public List<tblWineries> getTblWineries() {
		return tblWineries;
	}

	public tblCountries setTblWineries(List<tblWineries> tblWineries) {
		this.tblWineries = tblWineries;
		return this;
	}

	public List<tblAppellations> getAppellations() {
		return appellations;
	}

	public tblCountries setAppellations(List<tblAppellations> appellations) {
		this.appellations = appellations;
		return this;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((deleted == null) ? 0 : deleted.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((isoAlpha2Code == null) ? 0 : isoAlpha2Code.hashCode());
		result = prime * result + ((isoAlpha3Code == null) ? 0 : isoAlpha3Code.hashCode());
		result = prime * result + ((isoNumericCode == null) ? 0 : isoNumericCode.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		tblCountries other = (tblCountries) obj;
		if (deleted == null) {
			if (other.deleted != null)
				return false;
		} else if (!deleted.equals(other.deleted))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (isoAlpha2Code == null) {
			if (other.isoAlpha2Code != null)
				return false;
		} else if (!isoAlpha2Code.equals(other.isoAlpha2Code))
			return false;
		if (isoAlpha3Code == null) {
			if (other.isoAlpha3Code != null)
				return false;
		} else if (!isoAlpha3Code.equals(other.isoAlpha3Code))
			return false;
		if (isoNumericCode == null) {
			if (other.isoNumericCode != null)
				return false;
		} else if (!isoNumericCode.equals(other.isoNumericCode))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "tblCountries [id=" + id + ", name=" + name + ", isoAlpha2Code=" + isoAlpha2Code + ", isoAlpha3Code="
				+ isoAlpha3Code + ", isoNumericCode=" + isoNumericCode + ", deleted=" + deleted + ", currencies="
				+ currencies + ", languages=" + languages + ", timeZones=" + timeZones + ", users=" + users
				+ ", regions=" + regions + ", tblPFCountryNameMappingTables=" + tblPFCountryNameMappingTables
				+ ", countriesWithWines=" + countriesWithWines + ", tblWineries=" + tblWineries + ", appellations="
				+ appellations + "]";
	}
}
