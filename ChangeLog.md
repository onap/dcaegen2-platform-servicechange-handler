# Change Log

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/) 
and this project adheres to [Semantic Versioning](http://semver.org/).

## [1.3.2]

* DCAEGEN2-1718

## [1.3.0]

* Add non-root user in Docker image so that the inventory service can be run in non-privileged mode for security reasons DCAEGEN2-1555
* Change base image to alpine based DCAEGEN2-1566
* Support calling inventory using HTTPS DCAEGEN2-1597

## [1.1.3]

* DCAEGEN2-431

## [1.1.0]

This body of work is aimed at getting SCH to be run on the new DCAE platform.

* Add the ability to remotely fetch configuration in json form
* Add Dockerfile
