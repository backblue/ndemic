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
### Event dispatch and background work

`EventDispatcher` dispatches events without running application listeners on JDA's
WebSocket thread. Each shard has three independent, bounded, single-worker lanes:

| Lane | Queue capacity | Work |
| --- | ---: | --- |
| `Bot-Interactions-*` | 256 | Commands, buttons, menus, modals, autocomplete |
| `Bot-Messages-*` | 1024 | Message events, including the ordered moderation pipeline |
| `Bot-Events-*` | 512 | Other events, including lifecycle and membership events |

Listeners for an event run in their original registration order. Events in each
lane are processed in arrival order; there is no ordering guarantee between lanes.
State shared between lanes must therefore be thread-safe. The feature set uses a
concurrent set. Do not add slow I/O to interaction handlers: acknowledge promptly,
then submit slow work to a dedicated lane. Modal-opening handlers must respond
with the modal directly rather than defer first.

`Bot.getProfileScans()` provides a separate single-worker lane with capacity 128
for automatic profile scans, including banner analysis. There is no manual `/scan`
command. Google Vision
context lookups retain their separate worker. JDA REST callbacks remain on JDA's
callback pool; deferring an interaction alone does not offload subsequent work.

`TaskLane.submit` returns false on overload or shutdown, logs the rejection, and
never executes rejected work on the caller. Rejected reply-capable interactions
receive a busy response; other rejected events are logged and dropped.
Accepted events drain when their shard
shuts down. The profile lane stops accepting work at JVM shutdown; shutdown does
not guarantee completion of external network calls.

This reserves interaction capacity independently of moderation. It does not give
interactions OS thread priority or guarantee Discord response times. Slow command
or button handlers can still delay later interactions on their own lane.
