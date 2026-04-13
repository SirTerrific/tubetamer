FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Install runtime deps: gosu, ffmpeg, deno (JS runtime for yt-dlp)
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends gosu ffmpeg curl unzip && rm -rf /var/lib/apt/lists/* \
    && ARCH=$(dpkg --print-architecture) \
    && if [ "$ARCH" = "amd64" ]; then DENO_ARCH="x86_64-unknown-linux-gnu"; \
       elif [ "$ARCH" = "arm64" ]; then DENO_ARCH="aarch64-unknown-linux-gnu"; \
       else DENO_ARCH=""; fi \
    && if [ -n "$DENO_ARCH" ]; then \
         curl -fsSL "https://github.com/denoland/deno/releases/latest/download/deno-${DENO_ARCH}.zip" -o /tmp/deno.zip \
         && unzip -o /tmp/deno.zip -d /usr/local/bin/ \
         && chmod +x /usr/local/bin/deno \
         && rm /tmp/deno.zip \
         && echo "Deno installed: $(deno --version | head -1)"; \
       else echo "WARNING: No deno binary for $ARCH — yt-dlp may have limited format support"; fi \
    && useradd -r -m -s /bin/false appuser \
    && mkdir -p /app/db /app/db/videos /app/db/logs \
    && chown -R appuser:appuser /app

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
CMD ["sh", "-c", "if [ -f /app/config.yaml ]; then exec python main.py -c /app/config.yaml; else exec python main.py; fi"]
