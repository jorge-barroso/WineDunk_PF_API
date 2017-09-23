<%@ page language="java" contentType="text/html; charset=ISO-8859-1" pageEncoding="ISO-8859-1"%>
<%@ page isELIgnored="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
<title>Winedunk Product Feeds</title>
<!-- import generic head -->
<c:import url="generics/header.jsp"/>
</head>
<body>
	<div id=pfFormId>
		<form id="pfForm" onSubmit="submit()">
			
		</form>
	</div>
	<c:import url="generics/body.jsp"/>
	<table>
		<c:forEach items="${productFeeds}" var="productFeed">
			<tr>
				<td>
					<c:out value="${productFeed.tblPartners.name}"/>
				</td>
				<td>
					
				</td>
			</tr>
		</c:forEach>
	</table>
	
</body>
</html>