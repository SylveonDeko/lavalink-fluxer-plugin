# lavalink-fluxer-plugin

Lavalink 4 plugin that lets Fluxer bots play Lavalink audio. Fluxer voice runs on LiveKit, which Lavalink cannot
join, so the bot holds the voice connection and this plugin streams each player's audio to it.

- `GET /v4/fluxer/audio` (WebSocket, headers `Authorization` and `User-Id`) sends one binary message per 20 ms
  frame for every playing player of that user: the guild id as a big endian 64 bit integer, then 16 bit little
  endian stereo PCM at 48 kHz, decoded from the player's Opus frames.
- A player update whose `voice.endpoint` is `fluxer` has its `voice` field dropped, so Lavalink does not try to open
  a Discord voice connection for it. Fluxer clients send that placeholder instead of a real voice server.

Discord bots can share the node: their players keep their Opus output and their voice updates pass through unchanged.
The client side is [Lavalink4NET.Fluxer](https://github.com/SylveonDeko/Lavalink4NET.Fluxer).

## Building

```
./gradlew build
```

Copy `build/libs/lavalink-fluxer-plugin-*.jar` into the node's `plugins` directory.
