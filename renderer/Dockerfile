FROM node:22-slim

RUN npx playwright install --with-deps chromium

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install

COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build

EXPOSE 3000

CMD ["node", "dist/index.js"]
