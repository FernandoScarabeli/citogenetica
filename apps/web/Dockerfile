# A tag acompanha o Node 26 definido em .nvmrc, com versão e Alpine fixos.
FROM node:26.13.0-alpine3.24 AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# Imagem final contém apenas Nginx e os arquivos estáticos da aplicação.
FROM nginx:1.31.8-alpine3.24 AS runtime

RUN rm -f /etc/nginx/conf.d/default.conf && \
    mkdir -p \
      /tmp/nginx/client_temp \
      /tmp/nginx/proxy_temp \
      /tmp/nginx/fastcgi_temp \
      /tmp/nginx/uwsgi_temp \
      /tmp/nginx/scgi_temp && \
    chown -R 101:101 /tmp/nginx

COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build --chown=101:101 /app/dist/ /usr/share/nginx/html/

USER 101:101
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1

ENTRYPOINT ["nginx", "-g", "daemon off;"]
