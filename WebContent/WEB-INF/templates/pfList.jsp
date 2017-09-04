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
	<c:import url="generics/body.jsp"/>
	<table>
		<c:forEach items="${productFeeds}" var="productFeed">
			<tr>
				<td>
					<c:out value="${productFeed.partnerId}"/>
				</td>
				<td>
					
				</td>
			</tr>
		</c:forEach>
	</table>
</body>
</html>