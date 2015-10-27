<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.google.appengine.api.memcache.MemcacheService" %>
<%@ page import="com.google.appengine.api.memcache.MemcacheServiceFactory" %>
<%@ page import="net.greypanther.repomirror.Counter" %>
<!doctype html>

<html lang="en" ng-app="app">
<head>
	<meta charset="utf-8">

	<title>Repository Metadata Mirror</title>
	<meta name="description" content="Repository Metadata Mirror">

	<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css">
	<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap-theme.min.css">
	<script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/js/bootstrap.min.js"></script>

	<!--[if lt IE 9]>
		<script src="http://html5shiv.googlecode.com/svn/trunk/html5.js"></script>
	<![endif]-->

	<style>
	.vertical-align {
		display: flex;
		align-items: center;
	}
	</style>
</head>

<body>
	<%
		MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
	%>

	<div class="vertical-center"> 
		<div class="container">
			<h2>Mirroring the Maven repository metadata for statistical purposes</h2>
			<ul>
				<li>Download attempts: <%= Counter.get(Counter.Key.ATTEMPTS, memcache) %></li>
				<li>Stored: <%= Counter.get(Counter.Key.STORED, memcache) %></li>
				<li>Enqeued: <%= Counter.get(Counter.Key.ENQUEUED, memcache) %></li>
			</ul>
		</div>
	</div>
</body>
</html>