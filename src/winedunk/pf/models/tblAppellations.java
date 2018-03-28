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
@Table(name = "tblAppellations")
@NamedQuery(name="tblAppellations.findByName", query="SELECT t FROM tblAppellations t WHERE t.name = :name")
public class tblAppellations {

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

    @Column(name= "deleted")
    private Boolean deleted;
    public Boolean isDeleted() {return deleted;}
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }

    @OneToMany(mappedBy = "appellation", targetEntity = tblWines.class)
    @JsonBackReference
    private List<tblWines> wines;
    public List<tblWines> getWines() { return wines; }
	public void setWines(List<tblWines> wines) { this.wines = wines; }

	@ManyToOne
    @JoinColumn(name = "countryId")
    private tblCountries countryId;
    public tblCountries getCountryId() { return countryId; }
	public void setCountryId(tblCountries countryId) { this.countryId = countryId; }

	@ManyToOne
    @JoinColumn(name = "regionId")
    private tblRegions regionId;
    public tblRegions getRegionId() { return regionId; }
	public void setRegionId(tblRegions regionId) { this.regionId = regionId; }

    public tblAppellations(Integer id) { this.id = id; }
    public tblAppellations(String name) { this.name = name; }
    public tblAppellations()
    {
        this.id = null;
        this.name = null;
        this.deleted = null;
        this.wines = null;
        this.countryId = null;
    }

    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((countryId == null) ? 0 : countryId.hashCode());
		result = prime * result + ((deleted == null) ? 0 : deleted.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((regionId == null) ? 0 : regionId.hashCode());
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
		tblAppellations other = (tblAppellations) obj;
		if (countryId == null) {
			if (other.countryId != null)
				return false;
		} else if (!countryId.equals(other.countryId))
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
		return true;
	}
	@Override
	public String toString() {
		return "tblAppellations [id=" + id + ", name=" + name + ", deleted=" + deleted + ", wines=" + wines
				+ ", countryId=" + countryId + ", regionId=" + regionId + "]";
	}
}