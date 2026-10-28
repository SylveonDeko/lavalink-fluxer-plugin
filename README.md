# lavalink-fluxer-plugin

A Lavalink plugin for playing audio in [Fluxer](https://fluxer.app) voice channels.

Fluxer voice runs on LiveKit, which Lavalink can't connect to. The bot holds the voice connection itself and this
plugin streams each player's audio to it. Discord bots can use the same node.

For .NET bots, [Lavalink4NET.Fluxer](https://github.com/SylveonDeko/Lavalink4NET.Fluxer) is the client side.

## Lavalink Usage

This plugin requires Lavalink `v4.2.2` or higher.

Add it to your `application.yml` and Lavalink downloads it on startup. Replace x.y.z with the latest version number.

```yaml
lavalink:
  plugins:
    - dependency: "com.github.SylveonDeko:lavalink-fluxer-plugin:vx.y.z"
      repository: "https://jitpack.io"
```

Or download the jar from the [releases](https://github.com/SylveonDeko/lavalink-fluxer-plugin/releases) and place it
into your `plugins` folder.

## API

### Player updates

Send a player update with the placeholder voice server below. The plugin removes the `voice` field before Lavalink
sees it, and Lavalink plays the track without opening a Discord voice connection.

```json
{
  "voice": {
    "token": "fluxer",
    "endpoint": "fluxer",
    "sessionId": "..."
  }
}
```

Updates with any other endpoint are left alone.

### Audio stream

```
GET /v4/fluxer/audio
```

A WebSocket that sends the audio of every playing player owned by the connecting bot.

| Header          | Value                       |
|-----------------|-----------------------------|
| `Authorization` | The node password           |
| `User-Id`       | The bot's user id           |

Each binary message is one 20 ms frame:

| Bytes | Content                                          |
|-------|--------------------------------------------------|
| 0-7   | Guild id, 64 bit big endian                      |
| 8-    | PCM, 16 bit little endian, stereo, 48 kHz        |
