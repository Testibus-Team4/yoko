# The Yoko ORB
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## Build Requirements

1. JDK 11 or above (Note: the build will produce Java 7 compatible output, but the build process requires Java 11)

## Steps to build Yoko

1. From the root of the project, run `./gradlew build`

# Testify

Testify is an open-source generic framework used for the testing of Object Request Brokers (ORBs). It is based on an Apache Yoko fork, originally built for OpenLiberty. Yoko is an open-source project maintained by IBM, and is an implementation of an ORB system to carry out interprocess communication.

## Steps to build Testify

1. From the root of the project, run `./gradlew yoko-testify:build`
