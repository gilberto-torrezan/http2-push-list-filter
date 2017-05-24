# http2-push-list-filter
A servlet filter that pushes resources to the browser by using a list of URIs

## Read this before using this project

Before using this project to push resources to the browser, you definitely should check out the [PushCacheFilter from Jetty project](http://www.eclipse.org/jetty/documentation/9.4.x/http2-configuring-push.html). Test it. I mean it. Stop reading right now and go test that filter. Go.

Done? Ok, welcome back.

Did you get a performance improvement? Were your resources pushed the way you expected them to be pushed? Good, then you probably don't need this project.

But... If you didn't get any performance improvement in your application/webpage, or even worse, if you got a worse performance than without any push at all, then you definitely should try out this project.

## What is this project

This is a Servlet filter, just like Jetty's PushCacheFilter, that uses Jetty's HTTP2 API to push resources to the browser. But unlike PushCacheFilter, this filter uses a predefined list of what and when to push, and in what order.

This requires you to enumerate all the required files to push in a TXT file. Yes, this is cumbersome and hard to maintain, so make sure the automatic options really doesn't apply to you.

## How to use it

This project is not deployed to Maven Central yet (sorry for that), so you have to clone it and install in your own machine.

First of all, include it in your `pom.xml` file:

```
<dependency>
    <groupId>com.github.gilberto-torrezan</groupId>
    <artifactId>http2-push-list-filter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```
When deploying your project to Jetty, make sure the jars `http2-server-<jetty-version>.jar` and `jetty-http-<jetty-version>.jar` are in the classpath (usually inside `WEB-INF/lib`). Also make sure your Jetty is properly configured for HTTP2. Take a look at [this page](http://www.eclipse.org/jetty/documentation/9.4.x/http2-enabling.html) if you haven't configured it yet.

In your project, create a `jetty-web.xml` file at `src/main/webapp/WEB-INF` (the same place the `web.xml` file usually go), with this content:

```
<?xml version="1.0"  encoding="UTF-8"?>
<!DOCTYPE Configure PUBLIC "-//Jetty//Configure//EN" "http://www.eclipse.org/jetty/configure.dtd">

<Configure class="org.eclipse.jetty.webapp.WebAppContext">
  <Set name="parentLoaderPriority">true</Set>
  <Call name="addServerClass">
    <Arg>-org.eclipse.jetty.server.</Arg>
  </Call>
  <Call name="addServerClass">
    <Arg>-org.eclipse.jetty.http.</Arg>
  </Call>
</Configure>
``` 

This file is needed due to limitations on how the classloading proccess occurs inside Jetty. Take a look at [this article](https://www.eclipse.org/jetty/documentation/9.4.5.v20170502/jetty-classloading.html) for more details. 

Then, at your `web.xml` file, define the filter before anything else:

```
<?xml version="1.0" encoding="ISO-8859-1"?>
<web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee
         http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd"
         version="3.1">

    <filter>
        <filter-name>Http2PushListFilter</filter-name>
        <filter-class>com.github.gilbertotorrezan.http2.Http2PushListFilter</filter-class>
    </filter>
    <filter-mapping>
        <filter-name>Http2PushListFilter</filter-name>
        <url-pattern>/*</url-pattern>
    </filter-mapping>

</web-app>
```

The final step is to create the list of files to be pushed. Place a file named `http2-push.txt` at `src/main/webapp/WEB-INF` with the path of the files to be pushed (relative to the context):

```
/css/my-fancy-stylesheet.css
/js/my-unmaintainable-javascript.js
/img/my-heavy-but-beautiful-image.png
# and any other resources you want to push...
# Oh, the file supports comments too, as you see here
```

== Advanced usage

By default, all files listed at the `http2-push.txt` file will be pushed when the filter server receives the first HTTP2 GET request to `/`. But you can change this behavior, using multiple path with different resources to be pushed for each one:

 ```
# You have to use the "when" prefix to define multiple paths inside the file
when /
/css/my-fancy-stylesheet.css
/js/my-unmaintainable-javascript.js
/img/my-heavy-but-beautiful-image.png

when /login
/img/my-login-image.png

# You can use multiple paths (separated by whitespace) to avoid duplicating lists 
when /another-page /external
/js/my-fancier-stylesheet.css
/js/my-even-more-unmaintainable-javascript.js

```
If you start the file without a `when` prefix, the files listed at the top will be pushed for every path. For example:

 ```
# These will always be pushed
/css/always-pushed.css
/js/always-pushed.js

# And then you can set specific paths
when /login
/img/will-only-be-pushed-on-login.png

```
=== Warning!

When using multiple paths, remember to update the filter-mapping in your `web.xml` file accordingly. For example:

```
<?xml version="1.0" encoding="ISO-8859-1"?>
<web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee
         http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd"
         version="3.1">

    <filter>
        <filter-name>Http2PushListFilter</filter-name>
        <filter-class>com.github.gilbertotorrezan.http2.Http2PushListFilter</filter-class>
    </filter>
    <filter-mapping>
        <filter-name>Http2PushListFilter</filter-name>
        <url-pattern>/</url-pattern>
        <url-pattern>/login</url-pattern>
        <url-pattern>/another-page</url-pattern>
        <url-pattern>/external</url-pattern>
    </filter-mapping>

</web-app>
```
