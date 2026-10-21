# lavalink-fluxer-plugin

Lavalink 4 plugin that lets Fluxer bots play Lavalink audio. Fluxer voice runs on LiveKit, which Lavalink cannot
join, so the bot holds the voice connection and this plugin streams each player's audio to it.

- Players output 16 bit little endian stereo PCM at 48 kHz instead of Opus.
- The `voice` field of player updates is dropped, so Lavalink never tries to open a Discord voice connection.
- `GET /v4/fluxer/audio` (WebSocket, headers `Authorization` and `User-Id`) sends one binary message per 20 ms
  frame for every playing player of that user: the guild id as a big endian 64 bit integer, then the PCM.

Use it on a node dedicated to Fluxer bots, since Discord clients on the same node would get PCM instead of Opus.
The client side is [Lavalink4NET.Fluxer](https://github.com/SylveonDeko/Lavalink4NET.Fluxer).

## Building

```
./gradlew build
```

Copy `build/libs/lavalink-fluxer-plugin-*.jar` into the node's `plugins` directory.
