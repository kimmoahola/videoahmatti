FROM clojure:temurin-25-tools-deps

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ffmpeg \
        python3 \
        python3-pip \
        python3-venv \
#        libgomp1 \
#        libgl1 \
#        libglib2.0-0 \
#        libjpeg-dev \
#        libpng-dev \
    && rm -rf /var/lib/apt/lists/*

# RUN pipx install --no-cache-dir \
#     numpy \
#     pillow \
#     opencv-python-headless \
#     torch \
#     torchvision

RUN python3 -m venv venv
RUN venv/bin/pip install --no-cache-dir \
    speciesnet

#RUN venv/bin/python -m speciesnet.scripts.run_model --folders "folder" --predictions_json "file.json"
#RUN venv/bin/python -c "import torch, cv2, PIL, numpy; print('speciesnet-runtime-ok')"

COPY deps.edn ./
#RUN clojure -P

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
#CMD ["venv/bin/python", "-m", "speciesnet.scripts.run_model", "--country", "FIN", "--folders", "/app/videos", "--predictions_json", "/app/data/predictions.json"]
#CMD ["venv/bin/python", "-m", "speciesnet.scripts.run_model", "--help"]
