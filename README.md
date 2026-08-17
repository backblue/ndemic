# Discord Automoderator: Facilitator

Live on discord.com/ndemic, this multipurpose moderation and utility Discord Bot is a replacement for Dyno, Carl, and Mee6.

## Features
- **Gatekeeper (AI)** — Detection of burst-joining spambots.
- **Gatekeeper Require Onboarding** — Requires new members to complete server onboarding.
- **Gatekeeper Remove Low Quality Accounts** — Automatically prunes accounts flagged as low-quality/likely-bot.
- **Honeypot** — Hidden trap channel — any member who posts in it is flagged.
- **Disable DMs** — Disables direct messages between non-friended server members, to prevent DM-based scams/solicitations.
- **Scan Profiles (AI)** — Scans profile pictures for "hate".
- **Detect Crypto (AI)** — Scans messages/images for crypto scam content.
- **Raid Pause Invites** — Automatically pauses server invites when Discord's built-in raid detection triggers.
- **AutoMod Alerts** — Ping moderators when Discord Automod is triggered
- **Message Forwarding** — Forwards messages between channels.
- **Role Icons/Badges** — Allow members to freely choose between guild role icons
- **BlueSky** — Tracks and posts updates from BlueSky accounts
- **Nitro Boost Messages** — Sends a message when a member boosts the server with Nitro.
- **Enforce One Guide Access** — Lock down forum threads to limit postings to thread owners


## Setup & Deployment

There is a directory named `data` that needs to be present.

### Required:
Download `eng.traineddata` and `osd.traineddata` from [Tesseract](https://github.com/tesseract-ocr/tessdata), then drop them into `data/tessdata`.

### Then,

You can clone this project, then run to get a JAR:

`mvn clean package`

Rename `data/bot.txt` to `data/bot.properties`, then add keys for every value, then run.

Bot will generate required example configuration files.