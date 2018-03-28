package winedunk.pf.models;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonBackReference;


@Entity
@Table(name = "tblWineries")
@NamedQuery(name="tblWineries.findByName", query="SELECT t FROM tblWineries t WHERE t.name = :name")
public class tblWineries {

    @Transient
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    @Column(name= "name")
    private String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @ManyToOne
    @JoinColumn(name="countryId")
    private tblCountries tblCountry;
    public tblCountries getTblCountries() { return this.tblCountry; }
    public void setTblCountries(tblCountries tblCountries) { this.tblCountry = tblCountries; }

    private Integer regionId;
    public Integer getRegionId() { return this.regionId; }
    public void setRegionId(Integer regionId) { this.regionId = regionId; }

    private Integer appellationId;
    public Integer getAppellationId() { return this.appellationId; }
    public void setAppellationId(Integer appellationId) { this.appellationId = appellationId; }

    @Column(name= "deleted")
    private Boolean deleted;
    public Boolean isDeleted() {return deleted;}
    public void setDeleted(Boolean deleted) {this.deleted = deleted;}

    @OneToMany(mappedBy = "winery", targetEntity = tblWines.class)
    @JsonBackReference//("currency")
    private List<tblWines> wines;
    public List<tblWines> getWines() { return wines; }
	public void setWines(List<tblWines> wines) { this.wines = wines; }

    public tblWineries(Integer id) { this.id = id; }
    public tblWineries()
    {
        this.id = null;
        this.name = null;
        this.deleted = null;
        this.wines = null;
    }
    public tblWineries(String name) {
        this.name = name;
    }
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((appellationId == null) ? 0 : appellationId.hashCode());
		result = prime * result + ((deleted == null) ? 0 : deleted.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((regionId == null) ? 0 : regionId.hashCode());
		result = prime * result + ((tblCountry == null) ? 0 : tblCountry.hashCode());
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
		tblWineries other = (tblWineries) obj;
		if (appellationId == null) {
			if (other.appellationId != null)
				return false;
		} else if (!appellationId.equals(other.appellationId))
			return false;
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
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (regionId == null) {
			if (other.regionId != null)
				return false;
		} else if (!regionId.equals(other.regionId))
			return false;
		if (tblCountry == null) {
			if (other.tblCountry != null)
				return false;
		} else if (!tblCountry.equals(other.tblCountry))
			return false;
		return true;
	}
	@Override
	public String toString() {
		return "tblWineries [id=" + id + ", name=" + name + ", tblCountry=" + tblCountry + ", regionId=" + regionId
				+ ", appellationId=" + appellationId + ", deleted=" + deleted + ", wines=" + wines + "]";
	}
}