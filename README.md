# DCAE service change handler

Application that is responsible for polling for ASDC distribution notification events and handling those events.  Handling means:

* Parsing the event for DCAE artifacts
* Identifying whether its complementary DCAE service type resource in DCAE inventory has changed
* Taking action
    - Insert a new DCAE service type
    - Update an existing DCAE service type
    - Deactivate an existing DCAE service type
* Send appropriate acknowledgements back

## Dependencies

Uses the SDC distribution client to interface with the SDC API.

## To run

Two modes of operation: development and production.

### Development

The application in development mode does not actually pull from ASDC but rather takes in a file that contains a single ASDC notification event as a third argument and processes it.

Usage of development mode:

```
java -jar dcae-service-change-handler-0.1.0.jar dev <config file path> <event file path>
```

### Production

The application in production mode continuously pulls events from ASDC and processes them.

Usage of production mode when config is a file on the filesystem:

```
java -jar dcae-service-change-handler-0.1.0.jar prod <config file path>
```

Usage of production mode when config is remote stored in Consul:

```
java -jar dcae-service-change-handler-0.1.0.jar prod http://consul:8500/v1/kv/service-change-handler?raw=true
```

#### Use script

[`sch.sh`](resources/sch.sh) is a script to run service change handler that connects with inventory using HTTPS.  The script attempts to add a custom CA cert to the OS's key store `/usr/local/openjdk-11/lib/security/cacerts` and then launches service change handler.  The custom CA cert is used to validate the server-side cert provided by inventory at runtime.

The script uses the following environment variables:

Name | Description | Default
---- | ----------- | -------
`PATH_TO_CACERT` | Local file path to the CA cert that needs to be added to the keystore | `/opt/cert/cacert.pem`
`SCH_ARGS` | Args to be passed into the SCH run command | `prod http://consul:8500/v1/kv/service-change-handler?raw=true`
