# IPMI Poller

## Installing Verax IPMI library
1. Download Verax IPMI library from http://www.veraxsystems.com/en/downloads/ipmilibdownload
2. Add to local repository (beware GPL)
```
mvn install:install-file -Dfile=vxIPMI.jar -DgroupId=verax -DartifactId=commons-vxipmi-library -Dversion=1.0.15 -Dpackaging=jar
```

## Configuration
1. Modify example.yml accordingly, at the very least add your API key to the meterManagerClient.apiKey

## Running
```
mvn package
java -jar target/com.boundary.metrics.ipmi-1.0-SNAPSHOT.jar server example.yml

```