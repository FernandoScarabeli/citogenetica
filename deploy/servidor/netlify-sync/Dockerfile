FROM alpine:3.23.8
RUN apk add --no-cache curl jq
RUN mkdir /estado && chown 10001 /estado
COPY sync.sh /sync.sh
USER 10001
ENTRYPOINT ["/bin/sh", "/sync.sh"]
