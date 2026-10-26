# PintoMurderMystery
### The game

This is a clone of murder mystery gamemodes on a few popular bedrock and java servers. Sixteen players, two of which are murderers and one of which is a sheriff.
The murderer's goal is to kill all of the innocents (sheriff included), and the innocent's goal is to figure out who the murderers are and shoot them.

## Compatibility
Works on spigot servers MC26.3 (including forks - paper, purpur, etc.)

## Features
- Full murder mystery gamemode
- Easy setup and administration with commands
- Almost everything is configurable; number of players, murderers, sherrifs, throwing knife cooldown and speed, pregame grace period duration, etc.

## Getting Started

### Prerequisites
- Spigot or Paper (forks included) server on Minecraft 26.3
- Multiverse-core or other world manager

### Installation
1. Place the JAR file into your server's `plugins` directory.
2. Start/restart your server.

### Configuration

- Use the `/mm` command to customize and edit game settings, including new games, removing games, editing any setting, etc.
- The plugin will generate a default `config.yml` if one does not exist.

### Usage

- The plugin works automatically upon server start.
- When you join any world in config, you will be automatically added to the game.
- Game will start instantly when maxplayers are joined, or after 20s with minplayers online (configurable)

## [License](./LICENSE)

---

*Developed by PintoJ*