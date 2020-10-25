FROM gameontext/docker-liberty-custom:master-29

ENV SERVERDIRNAME room

COPY ./startup.sh /opt/startup.sh
ADD ./servers/gameon-room /opt/ol/wlp/usr/servers/defaultServer/

USER 0 
RUN chmod g+rwx /opt/ol/wlp/usr/servers/defaultServer/apps/room-app.war
USER 1001

CMD ["/opt/startup.sh"]

HEALTHCHECK \
  --timeout=10s \
  --start-period=40s \
  CMD wget -q -O /dev/null http://localhost:9080/rooms/health
