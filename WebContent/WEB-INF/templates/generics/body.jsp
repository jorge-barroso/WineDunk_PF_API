<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page isELIgnored ="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!--div id="headerDiv" style="width: 100%;" class="jumbotron">
	<img style="width: 80%; display: block; margin: 0 auto;" alt="Report Service" src="resources/td_values_logo.png">
</div-->
<div class="navbar-no-overlap">
<nav class="navbar navbar-default navbar-fixed-top">
  <div class="container-fluid">
    <div class="navbar-header">
      <a class="navbar-brand a-withBackground" href="/">Product Feeds</a>
    </div>
    <ul class="nav navbar-nav ">
      <li class="a-withBackground"><a href="/">Home</a></li>
      <li class="a-withBackground"><a href="/">New Product Feed</a></li>
    </ul>
    <ul class="nav navbar-nav navbar-right">
      <c:choose>
      	<c:when test="${sessionScope.loggedIn == true}">
      		<li class="a-withBackground"><a href="/Logout"><span class="glyphicon glyphicon-log-out"></span> Log out</a></li>
      	</c:when>
      	<c:otherwise>
      		<li class="a-withBackground"><a href="/Login"><span class="glyphicon glyphicon-log-in"></span> Log in</a></li>
      	</c:otherwise>
      </c:choose>
    </ul>
  </div>
</nav>
</div>