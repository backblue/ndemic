# Ndemic Bot

## Features
### Moderation
- :white_check_mark: Get notified when AutoMod is executed
- :white_check_mark: Enforce user-created guide threads
- :white_check_mark: Enforce roles before users can post, epically those who joined before Onboarding
- :white_check_mark: Uses Microsoft Azure's content moderation API help moderate guilds

## Setup
You'll need to compile and set up the bot yourself.

1. Clone the repository and navigate to the root directory of the project. Then compile with `mvn clean build`.
2. Move your executable jar to the directory of your choice (an empty is preferred).
3. Create a new folder with name `data`, and copy-paste all the files into that directory
4. Create a new folder with name `data/users`.
5. Create your bot, and get it's token from here in [discord's developer portal](https://discord.com/developers/applications/)
6. Create a new file: `data/keys.properties` and paste the following into it, and then run.
```
TOKEN=
AZURE_SAFETY_ENDPOINT=
AZURE_SAFETY_KEY=
JDBC=
``` 

Note: IntellJ Artifacts will no longer be supported to build the executable jar.