FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .

# ✅ COPY . . 이후, RUN npm run build 이전에 선언해야 함
ARG VITE_API_BASE_URL
ARG VITE_AUTH_BASE_URL
ARG VITE_GRAFANA_BASE_URL
ARG VITE_GRAFANA_DASHBOARD_UID

RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
USER 101:101
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
