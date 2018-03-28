package winedunk.pf.models;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import winedunk.pf.helpers.ContentTypes;

@Entity
@Table(name="tblDataSources")
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class DataSource {

	@Id
	@Column
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;

	@Column(name="loginUrl")
	private String loginUrl;

	@Column(name="tokenField")
	private String tokenField;

	@Column(name="authField")
	private String authField;

	@Column(name="dataUrl")
	private String dataUrl;

	@NotNull
	@Column(name="contentType")
	@Enumerated(EnumType.STRING)
	private ContentTypes contentType;

	@OneToMany(mappedBy="dataSource", targetEntity=tblShops.class)
	private List<tblShops> shops;

	@OneToMany(mappedBy="dataSource", targetEntity=DataSourceParam.class)
	private List<DataSourceParam> dataSourceParams;

	public DataSource()
	{
		super();
	}

	public DataSource(Integer id, String loginUrl, String tokenField, String authField, String dataUrl, ContentTypes contentType) {
		super();
		this.id = id;
		this.loginUrl = loginUrl;
		this.tokenField = tokenField;
		this.authField= authField;
		this.dataUrl = dataUrl;
		this.contentType = contentType;
	}

	public Integer getId()
	{
		return id;
	}

	public void setId(Integer id)
	{
		this.id = id;
	}

	public String getLoginUrl()
	{
		return loginUrl;
	}

	public void setLoginUrl(String loginUrl)
	{
		this.loginUrl = loginUrl;
	}

	public String getTokenField() {
		return tokenField;
	}

	public void setTokenField(String tokenField) {
		this.tokenField = tokenField;
	}

	public String getAuthField() {
		return authField;
	}

	public void setAuthField(String authField) {
		this.authField = authField;
	}

	public String getDataUrl()
	{
		return dataUrl;
	}

	public void setDataUrl(String dataUrl)
	{
		this.dataUrl = dataUrl;
	}

	public ContentTypes getContentType()
	{
		return contentType;
	}

	public void setContentType(ContentTypes contentType)
	{
		this.contentType = contentType;
	}

	public List<tblShops> getShops() {
		return shops;
	}

	public void setShops(List<tblShops> shops) {
		this.shops = shops;
	}

	public List<DataSourceParam> getDataSourceParams() {
		return dataSourceParams;
	}

	public void setDataSourceParams(List<DataSourceParam> dataSourceParams) {
		this.dataSourceParams = dataSourceParams;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((authField == null) ? 0 : authField.hashCode());
		result = prime * result + ((contentType == null) ? 0 : contentType.hashCode());
		result = prime * result + ((dataUrl == null) ? 0 : dataUrl.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((loginUrl == null) ? 0 : loginUrl.hashCode());
		result = prime * result + ((tokenField == null) ? 0 : tokenField.hashCode());
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
		DataSource other = (DataSource) obj;
		if (authField == null) {
			if (other.authField != null)
				return false;
		} else if (!authField.equals(other.authField))
			return false;
		if (contentType != other.contentType)
			return false;
		if (dataUrl == null) {
			if (other.dataUrl != null)
				return false;
		} else if (!dataUrl.equals(other.dataUrl))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (loginUrl == null) {
			if (other.loginUrl != null)
				return false;
		} else if (!loginUrl.equals(other.loginUrl))
			return false;
		if (tokenField == null) {
			if (other.tokenField != null)
				return false;
		} else if (!tokenField.equals(other.tokenField))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "DataSource [id=" + id + ", loginUrl=" + loginUrl + ", tokenField=" + tokenField + ", authField="
				+ authField + ", dataUrl=" + dataUrl + ", contentType=" + contentType + "]";
	}
}
