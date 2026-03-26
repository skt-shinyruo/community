FROM node:20-alpine

WORKDIR /app

COPY tools/mock-data-studio/package.json tools/mock-data-studio/package-lock.json ./
RUN npm ci --omit=dev

COPY tools/mock-data-studio/src ./src
COPY tools/mock-data-studio/README.md ./README.md

EXPOSE 12888

CMD ["npm", "run", "start"]
