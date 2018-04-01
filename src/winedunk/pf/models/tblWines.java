package winedunk.pf.models;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NamedStoredProcedureQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

@Entity
@Table(name = "tblWines")
@NamedQueries({ @NamedQuery(name = "tblWines.FindByGtin", query = "SELECT t FROM tblWines t WHERE t.gtin = :gtin"),
		@NamedQuery(name = "tblWines.FindByNameBottleAndVintage", query = "SELECT t FROM tblWines t "
																		+ "WHERE t.name = :name "
																		+ "AND (t.bottleSize = :bottleSize or (:bottleSize IS NULL AND t.bottleSize IS NULL)) "
																		+ "AND (t.vintage = :vintage or (:vintage IS NULL AND t.vintage IS NULL))") })
@NamedStoredProcedureQuery(name = "setMinimumPrices", procedureName = "spUpdateMinPriceOntblWines")
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public final class tblWines {
	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "countryId")
	private tblCountries country;

	@ManyToOne
	@JoinColumn(name = "regionId")
	private tblRegions region;

	@ManyToOne
	@JoinColumn(name = "appellationId")
	private tblAppellations appellation;

	@ManyToOne
	@JoinColumn(name = "colourId")
	private tblColours colour;

	@OneToMany(mappedBy = "tblWines", targetEntity = TblWinesWineType.class)
	@JsonBackReference
	private Set<TblWinesWineType> tblWinesWineType;

	@ManyToOne
	@JoinColumn(name = "wineryId")
	private tblWineries winery;

	@ManyToOne
	@JoinColumn(name = "closureId")
	private tblClosures closure;

	@OneToMany(mappedBy = "wine", targetEntity = TblWinesGrapeVariety.class)
	private Set<TblWinesGrapeVariety> tblWinesGrapeVariety;

	@Column(name = "name", nullable = false)
	@NotNull
	private String name;

	@Column(name = "defaultDescription")
	private String defaultDescription;

	@Column(name = "shortDescription")
	private String shortDescription;

	@Column(name = "bottleSize")
	private Float bottleSize;

	@Column(name = "vintage")
	private Integer vintage;

	@Column(name = "abv")
	private Float abv;

	@Column(name = "imageURL")
	private String imageURL;

	@Column(name = "gtin")
	private String gtin;

	@Column(name = "minimumPrice")
	private Float minimumPrice;

	@ManyToOne
	@JoinColumn(name = "minimumPriceShopId")
	private tblShops minimumPriceShopId;

	@Column(name = "deleted")
	private Boolean deleted;

	@OneToMany(mappedBy = "wineId", targetEntity = tblUserFavouriteWines.class)
	@JsonBackReference("wine_favouriteWine")
	private List<tblUserFavouriteWines> favouriteWines;

	@OneToMany(mappedBy = "wineId", targetEntity = tblUserWinesRatings.class)
	@JsonBackReference("wine_wineRatings")
	private List<tblUserWinesRatings> wineRatings;

	@OneToMany(mappedBy = "wineId", targetEntity = tblUserWineReviews.class)
	@JsonBackReference("wine_wineReviews")
	private List<tblUserWineReviews> reviews;

	@OneToMany(mappedBy = "wineId", targetEntity = tblUserWinesViewed.class)
	@JsonBackReference("wine_winesViewed")
	private List<tblUserWinesViewed> winesViewed;

	@OneToMany(mappedBy = "wineId", targetEntity = tblClicks.class)
	@JsonBackReference("wine_clicks")
	private List<tblClicks> clicks;

	@OneToMany(mappedBy = "wineId", targetEntity = tblUserPriceAlerts.class)
	@JsonBackReference("wine_priceAlerts")
	private List<tblUserPriceAlerts> userPriceAlerts;

	@OneToMany(mappedBy = "wineId", targetEntity = tblRecommendedWines.class)
	@JsonBackReference("RecommendedWines")
	private List<tblRecommendedWines> recommendedWines;

	@OneToMany(mappedBy = "wineId", targetEntity = tblWinesbyMerchants.class)
	@JsonBackReference("wine_winesByMerchant")
	private List<tblWinesbyMerchants> winesByMerchant;

	@OneToMany(mappedBy = "wineId", targetEntity = tblBestOffersbyType.class)
	@JsonBackReference("wine_bestOffersByType")
	private List<tblBestOffersbyType> bestOffersByType;

	@Column(name = "avgRating")
	private Float avgRating;
	
	public tblWines(Integer id) {
		this();
		this.id = id;
	}

	public tblWines() {
		this.id = null;
		this.country = null;
		this.name = null;
		this.defaultDescription = null;
		this.bottleSize = null;
		this.abv = null;
		this.gtin = null;
		this.imageURL = null;
		this.deleted = false;
		this.favouriteWines = null;
		this.recommendedWines = null;
		this.reviews = null;
		this.winesViewed = null;
		this.clicks = null;
		this.minimumPrice = null;
		this.minimumPriceShopId = null;
		this.userPriceAlerts = null;
		this.winesByMerchant = null;
		this.bestOffersByType = null;
		this.avgRating = null;
		
		this.tblWinesGrapeVariety = new HashSet<TblWinesGrapeVariety>(3);
	}

	public tblWines(String name) {
		this();
		this.name = name;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public tblCountries getCountry() {
		return country;
	}

	public void setCountry(tblCountries country) {
		this.country = country;
	}

	public tblRegions getRegion() {
		return region;
	}

	public void setRegion(tblRegions region) {
		this.region = region;
	}

	public tblAppellations getAppellation() {
		return appellation;
	}

	public void setAppellation(tblAppellations appellation) {
		this.appellation = appellation;
	}

	public tblColours getColour() {
		return colour;
	}

	public void setColour(tblColours colour) {
		this.colour = colour;
	}

	public Set<TblWinesWineType> getTblWinesWineType() {
		return tblWinesWineType;
	}

	public void setTblWinesWineType(Set<TblWinesWineType> tblWinesWineType) {
		this.tblWinesWineType = tblWinesWineType;
	}

	public tblWineries getWinery() {
		return winery;
	}

	public void setWinery(tblWineries winery) {
		this.winery = winery;
	}

	public tblClosures getClosure() {
		return closure;
	}

	public void setClosure(tblClosures closure) {
		this.closure = closure;
	}

	public Set<TblWinesGrapeVariety> getTblWinesGrapeVariety() {
		return tblWinesGrapeVariety;
	}

	public void setTblWinesGrapeVariety(Set<TblWinesGrapeVariety> tblWinesGrapeVariety) {
		this.tblWinesGrapeVariety = tblWinesGrapeVariety;
	}

	public void addTblWinesGrapeVariety(TblWinesGrapeVariety tblWinesGrapeVariety) {
		this.tblWinesGrapeVariety.add(tblWinesGrapeVariety);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDefaultDescription() {
		return defaultDescription;
	}

	public void setDefaultDescription(String defaultDescription) {
		this.defaultDescription = defaultDescription;
	}

	public String getShortDescription() {
		return shortDescription;
	}

	public void setShortDescription(String shortDescription) {
		this.shortDescription = shortDescription;
	}

	public Float getBottleSize() {
		return bottleSize;
	}

	public void setBottleSize(Float bottleSize) {
		this.bottleSize = bottleSize;
	}

	public Integer getVintage() {
		return vintage;
	}

	public void setVintage(Integer vintage) {
		this.vintage = vintage;
	}

	public Float getAbv() {
		return abv;
	}

	public void setAbv(Float abv) {
		this.abv = abv;
	}

	public String getImageURL() {
		return imageURL;
	}

	public void setImageURL(String imageURL) {
		this.imageURL = imageURL;
	}

	public String getGtin() {
		return gtin;
	}

	public void setGtin(String gtin) {
		this.gtin = gtin;
	}

	public Float getMinimumPrice() {
		return minimumPrice;
	}

	public void setMinimumPrice(Float minimumPrice) {
		this.minimumPrice = minimumPrice;
	}

	public tblShops getMinimumPriceShopId() {
		return minimumPriceShopId;
	}

	public void setMinimumPriceShopId(tblShops minimumPriceShopId) {
		this.minimumPriceShopId = minimumPriceShopId;
	}

	public Boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(Boolean deleted) {
		this.deleted = deleted;
	}

	public List<tblUserFavouriteWines> getFavouriteWines() {
		return favouriteWines;
	}

	public void setFavouriteWines(List<tblUserFavouriteWines> favouriteWines) {
		this.favouriteWines = favouriteWines;
	}

	public List<tblUserWinesRatings> getWineRatings() {
		return wineRatings;
	}

	public void setWineRatings(List<tblUserWinesRatings> wineRatings) {
		this.wineRatings = wineRatings;
	}

	public List<tblUserWineReviews> getReviews() {
		return reviews;
	}

	public void setReviews(List<tblUserWineReviews> reviews) {
		this.reviews = reviews;
	}

	public List<tblUserWinesViewed> getWinesViewed() {
		return winesViewed;
	}

	public void setWinesViewed(List<tblUserWinesViewed> winesViewed) {
		this.winesViewed = winesViewed;
	}

	public List<tblClicks> getClicks() {
		return clicks;
	}

	public void setClicks(List<tblClicks> clicks) {
		this.clicks = clicks;
	}

	public List<tblUserPriceAlerts> getUserPriceAlerts() {
		return userPriceAlerts;
	}

	public void setUserPriceAlerts(List<tblUserPriceAlerts> userPriceAlerts) {
		this.userPriceAlerts = userPriceAlerts;
	}

	public List<tblRecommendedWines> getRecommendedWines() {
		return recommendedWines;
	}

	public void setRecommendedWines(List<tblRecommendedWines> recommendedWines) {
		this.recommendedWines = recommendedWines;
	}

	public List<tblWinesbyMerchants> getWinesByMerchant() {
		return winesByMerchant;
	}

	public void setWinesByMerchant(List<tblWinesbyMerchants> winesByMerchant) {
		this.winesByMerchant = winesByMerchant;
	}

	public List<tblBestOffersbyType> getBestOffersByType() {
		return bestOffersByType;
	}

	public void setBestOffersByType(List<tblBestOffersbyType> bestOffersByType) {
		this.bestOffersByType = bestOffersByType;
	}

	public Float getAvgRating() {
		return avgRating;
	}

	public void setAvgRating(Float avgRating) {
		this.avgRating = avgRating;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((abv == null) ? 0 : abv.hashCode());
		result = prime * result + ((appellation == null) ? 0 : appellation.hashCode());
		result = prime * result + ((avgRating == null) ? 0 : avgRating.hashCode());
		result = prime * result + ((bottleSize == null) ? 0 : bottleSize.hashCode());
		result = prime * result + ((closure == null) ? 0 : closure.hashCode());
		result = prime * result + ((colour == null) ? 0 : colour.hashCode());
		result = prime * result + ((country == null) ? 0 : country.hashCode());
		result = prime * result + ((defaultDescription == null) ? 0 : defaultDescription.hashCode());
		result = prime * result + ((deleted == null) ? 0 : deleted.hashCode());
		result = prime * result + ((gtin == null) ? 0 : gtin.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((imageURL == null) ? 0 : imageURL.hashCode());
		result = prime * result + ((minimumPrice == null) ? 0 : minimumPrice.hashCode());
		result = prime * result + ((minimumPriceShopId == null) ? 0 : minimumPriceShopId.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((region == null) ? 0 : region.hashCode());
		result = prime * result + ((shortDescription == null) ? 0 : shortDescription.hashCode());
		result = prime * result + ((vintage == null) ? 0 : vintage.hashCode());
		result = prime * result + ((winery == null) ? 0 : winery.hashCode());
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
		tblWines other = (tblWines) obj;
		if (abv == null) {
			if (other.abv != null)
				return false;
		} else if (!abv.equals(other.abv))
			return false;
		if (appellation == null) {
			if (other.appellation != null)
				return false;
		} else if (!appellation.equals(other.appellation))
			return false;
		if (avgRating == null) {
			if (other.avgRating != null)
				return false;
		} else if (!avgRating.equals(other.avgRating))
			return false;
		if (bottleSize == null) {
			if (other.bottleSize != null)
				return false;
		} else if (!bottleSize.equals(other.bottleSize))
			return false;
		if (closure == null) {
			if (other.closure != null)
				return false;
		} else if (!closure.equals(other.closure))
			return false;
		if (colour == null) {
			if (other.colour != null)
				return false;
		} else if (!colour.equals(other.colour))
			return false;
		if (country == null) {
			if (other.country != null)
				return false;
		} else if (!country.equals(other.country))
			return false;
		if (defaultDescription == null) {
			if (other.defaultDescription != null)
				return false;
		} else if (!defaultDescription.equals(other.defaultDescription))
			return false;
		if (deleted == null) {
			if (other.deleted != null)
				return false;
		} else if (!deleted.equals(other.deleted))
			return false;
		if (gtin == null) {
			if (other.gtin != null)
				return false;
		} else if (!gtin.equals(other.gtin))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (imageURL == null) {
			if (other.imageURL != null)
				return false;
		} else if (!imageURL.equals(other.imageURL))
			return false;
		if (minimumPrice == null) {
			if (other.minimumPrice != null)
				return false;
		} else if (!minimumPrice.equals(other.minimumPrice))
			return false;
		if (minimumPriceShopId == null) {
			if (other.minimumPriceShopId != null)
				return false;
		} else if (!minimumPriceShopId.equals(other.minimumPriceShopId))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (region == null) {
			if (other.region != null)
				return false;
		} else if (!region.equals(other.region))
			return false;
		if (shortDescription == null) {
			if (other.shortDescription != null)
				return false;
		} else if (!shortDescription.equals(other.shortDescription))
			return false;
		if (vintage == null) {
			if (other.vintage != null)
				return false;
		} else if (!vintage.equals(other.vintage))
			return false;
		if (winery == null) {
			if (other.winery != null)
				return false;
		} else if (!winery.equals(other.winery))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "tblWines [id=" + id + ", country=" + country + ", region=" + region + ", appellation=" + appellation
				+ ", colour=" + colour + ", winery=" + winery + ", closure=" + closure + ", name=" + name
				+ ", defaultDescription=" + defaultDescription + ", shortDescription=" + shortDescription
				+ ", bottleSize=" + bottleSize + ", vintage=" + vintage + ", abv=" + abv + ", imageURL=" + imageURL
				+ ", gtin=" + gtin + ", minimumPrice=" + minimumPrice + ", minimumPriceShopId=" + minimumPriceShopId
				+ ", deleted=" + deleted + "]";
	}
}