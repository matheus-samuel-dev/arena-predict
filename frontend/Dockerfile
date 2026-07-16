FROM node:22-alpine AS build

WORKDIR /app

COPY package*.json ./

RUN npm ci

COPY . .

ARG VITE_API_URL=/api
ARG VITE_APP_NAME=ArenaPredict
ARG VITE_APP_SHORT_NAME=Arena
ARG VITE_APP_TAGLINE="Sua leitura. Sua arena."
ARG VITE_APP_DESCRIPTION="Previsões esportivas, bolões e ligas com pontos exclusivamente virtuais."
ARG VITE_DEMO_MODE=false
ENV VITE_API_URL=${VITE_API_URL}
ENV VITE_APP_NAME=${VITE_APP_NAME}
ENV VITE_APP_SHORT_NAME=${VITE_APP_SHORT_NAME}
ENV VITE_APP_TAGLINE=${VITE_APP_TAGLINE}
ENV VITE_APP_DESCRIPTION=${VITE_APP_DESCRIPTION}
ENV VITE_DEMO_MODE=${VITE_DEMO_MODE}

RUN npm run build


FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
