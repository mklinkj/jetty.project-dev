# DO NOT EDIT THIS FILE - See: https://eclipse.dev/jetty/documentation/

[description]
Enables an un-assembled Maven webapp to run in a Jetty distribution.

[environment]
ee8

[depends]
server
ee8-webapp
ee8-annotations

[lib]
lib/ee8-maven/**.jar

[xml]
etc/jetty-ee8-maven.xml
