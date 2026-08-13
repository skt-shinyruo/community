ARG GARAGE_IMAGE=dxflrs/garage:v2.2.0
FROM ${GARAGE_IMAGE} AS garage

FROM alpine:3.20
COPY --from=garage /garage /garage
ENTRYPOINT ["/bin/sh", "/bootstrap-garage.sh"]
