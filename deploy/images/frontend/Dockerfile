FROM node:20-alpine AS frontend-build

WORKDIR /workspace/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend ./
RUN npm run build

FROM nginx:1.27-alpine AS frontend-runtime

RUN apk add --no-cache jq \
    && rm -f /etc/nginx/conf.d/default.conf

COPY deploy/images/frontend/nginx.conf /etc/nginx/nginx.conf
COPY deploy/images/frontend/render-runtime-config.sh /usr/local/bin/render-runtime-config
COPY deploy/images/frontend/frontend-entrypoint.sh /usr/local/bin/frontend-entrypoint
COPY --from=frontend-build /workspace/frontend/dist /usr/share/nginx/html

RUN chmod 0755 /usr/local/bin/render-runtime-config /usr/local/bin/frontend-entrypoint

USER 101:101

EXPOSE 12881

HEALTHCHECK --interval=10s --timeout=3s --retries=30 \
  CMD wget -q -O /dev/null http://127.0.0.1:12881/app-config.js || exit 1

ENTRYPOINT ["/usr/local/bin/frontend-entrypoint"]
CMD ["nginx", "-g", "daemon off;"]
