FROM clojure:temurin-25-tools-deps

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ffmpeg \
        python3 \
        python3-pip \
        python3-venv \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt ./

COPY deps.edn ./
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
