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
