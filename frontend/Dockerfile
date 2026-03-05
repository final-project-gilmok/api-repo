FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# ✅ nginx:alpine → nginxinc/nginx-unprivileged:alpine 으로 변경
FROM nginxinc/nginx-unprivileged:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
# ✅ 80 → 8080 으로 변경 (비권한 포트)
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
