# Running Without Docker

If you'd rather run TubeTamer directly with Python instead of Docker Compose:

**Requires:** Python 3.11 or newer

```bash
git clone https://github.com/SirTerrific/tubetamer.git
cd TubeTamer

# Install dependencies
pip install -r requirements.txt

# Set up secrets and config
cp .env.example .env
# Edit .env with your bot token and chat ID

cp config.example.yaml config.yaml
# Edit config.yaml if you want to change defaults

# Run
export $(cat .env | xargs) && python main.py -c config.yaml
```

To keep it running in the background, use `screen`, `tmux`, or set up a systemd service.
