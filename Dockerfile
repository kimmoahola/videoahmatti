FROM clojure:temurin-25-tools-deps-alpine

WORKDIR /app

RUN apk add --no-cache ffmpeg

COPY deps.edn ./
RUN clojure -P

COPY resources ./resources
COPY src ./src

RUN mkdir -p /app/data /app/videos

ENV VIDEOAHMATTI_HOST=0.0.0.0 \
    VIDEOAHMATTI_PORT=8080 \
    VIDEOAHMATTI_VIDEO_ROOT=/app/videos \
    VIDEOAHMATTI_JDBC_URL=jdbc:sqlite:/app/data/videoahmatti.db

VOLUME ["/app/videos", "/app/data"]

EXPOSE 8080

CMD ["clojure", "-M:run"]
