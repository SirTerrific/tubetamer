# Running Without Docker

If you'd rather run TubeTamer directly with Python instead of Docker Compose:

**Requires:** Python 3.11 or newer — the Docker image and CI run 3.14, which is what the test suite is verified against. For local playback you also need `ffmpeg` on the PATH.

```bash
git clone https://github.com/SirTerrific/tubetamer.git
cd tubetamer

# Install dependencies
pip install -r requirements.txt

# Set up secrets and config
cp .env.example .env
# Edit .env with your bot token and chat ID

cp config.example.yaml config.yaml
# Edit config.yaml if you want to change defaults

# Run
set -a; source .env; set +a
python main.py -c config.yaml
```

To keep it running in the background, use `screen`, `tmux`, or set up a systemd service.
