#!/bin/bash

export CONTAINER_NAME=recroom

SERVER_PATH=/opt/ol/wlp/usr/servers/defaultServer

export ROOT_SERVICE_URL=ws://r410.kozow.com:7777/yamlroom
export MAP_SERVICE_URL=https://gameontext.org/map/v1/sites
export MAP_HEALTH_SERVICE_URL=https://gameontext.org/map/v1/health
export MAP_KEY=xxx
export SYSTEM_ID=xxx

exec /opt/ol/wlp/bin/server run defaultServer
