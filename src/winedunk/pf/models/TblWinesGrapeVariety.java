package winedunk.pf.models;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

/**
 * Entity implementation class for Entity: TblGrapeVariety
 *
 */
@Entity
@Table(name="tblWinesGrapeVarieties")
@NamedQueries({
	@NamedQuery(name="tblWinesGrapeVarieties.findAll", query="SELECT wg FROM TblWinesGrapeVariety wg"),
	@NamedQuery(name="tblWinesGrapeVarieties.findByWineAndGrape", query="SELECT wg FROM TblWinesGrapeVariety wg WHERE wg.wine = :wine AND wg.grapeVariety = :grape")
})
@JsonIdentityInfo(generator=ObjectIdGenerators.IntSequenceGenerator.class, property="@id")
@JsonIgnoreProperties(ignoreUnknown = true)
public class TblWinesGrapeVariety implements Serializable {	
	private static final long serialVersionUID = 1L;

	@ManyToOne
	@JoinColumn(name = "wineId")
	private tblWines wine;

	@ManyToOne
	@JoinColumn(name = "grapeVarietyId")
	private tblGrapeVarieties grapeVariety;
	
	public tblWines getWine() {
		return wine;
	}
	
	public void setWine(tblWines wine) {
		this.wine = wine;
	}
	
	public tblGrapeVarieties getGrapeVariety() {
		return grapeVariety;
	}
	
	public void setGrapeVariety(tblGrapeVarieties grapeVariety) {
		this.grapeVariety = grapeVariety;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((grapeVariety == null) ? 0 : grapeVariety.hashCode());
		result = prime * result + ((wine == null) ? 0 : wine.hashCode());
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
		TblWinesGrapeVariety other = (TblWinesGrapeVariety) obj;
		if (grapeVariety == null) {
			if (other.grapeVariety != null)
				return false;
		} else if (!grapeVariety.equals(other.grapeVariety))
			return false;
		if (wine == null) {
			if (other.wine != null)
				return false;
		} else if (!wine.equals(other.wine))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "TblWinesGrapeVariety [wine=" + wine + ", grapeVariety=" + grapeVariety + "]";
	}
}