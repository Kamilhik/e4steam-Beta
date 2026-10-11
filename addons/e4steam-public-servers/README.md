# e4steam Public Servers

An experimental e4steam addon with an in-game catalog of public player-hosted worlds, discovered directly through Steam.

## Status

`0.1.0-beta.10` uses Steam matchmaking by default. No VPS, domain, Web API key or registry URL is required. It needs the matching **e4steam 0.3.2-beta.1** core (or a later core containing the implemented lobby service); stock 0.3.2 cannot provide it. Addon API stays **1.1.0**.

This is a local development build, not a multiplayer-verified release. Direct Steam publication currently covers **player-hosted worlds only**. Headless dedicated-server discovery is not implemented by this backend; the dedicated tab will be empty.

## Features

- native Public Servers button on the Multiplayer screen;
- searchable, filterable catalog of player-hosted worlds;
- compatibility checks for Minecraft, loader, e4steam wire protocol and required addons;
- short-lived local join-attempt IDs; every connection still passes core Steam authentication and admission checks;
- a third **Public Server** access choice with explicit confirmation;
- lease heartbeats, automatic unpublication and stale-listing cleanup;
- local hide/block controls (remote moderation reports are unavailable in Steam mode);
- English and Russian interface;
- optional HTTP registry development backend, separate from the default Steam catalog.

## Build targets

| Minecraft | Loader | Java | Addon JAR marker |
|---|---|---:|---|
| 1.17–1.18.2 | Fabric / Quilt | 16+ | `fabric-quilt-mc1.17-1.18.2` |
| 1.17.1–1.18.1 | Forge | 16+ | `forge-mc1.17.1-1.18.1` |
| 1.19–1.21.11 | Fabric / Quilt | 17+ | `fabric-quilt-mc1.19-1.21.11` |
| 1.18.2–1.20.2 | Forge | 17+ | `forge-mc1.18.2-1.20.2` |
| 1.20.2–1.21.11 | NeoForge | 17+ | `neoforge-mc1.20.2-1.21.11` |
| 26.1–26.2 | Fabric / Quilt | 25+ | `fabric-quilt-mc26.1-26.2` |
| 26.1–26.2 | NeoForge | 25+ | `neoforge-mc26.1-26.2` |

All variants require the updated core and Addon API 1.1. A successful artifact build is not an in-game check of every version. The Java 8 retro e4steam builds for Minecraft 1.7–1.16 do not yet expose the required addon discovery/access modes.

## Use the Steam catalog

1. Install the matching core and addon on both computers; run Steam on both.
2. Host: open a world, choose **Public Server** in Open to LAN, then confirm publication.
3. Guest: open Multiplayer → Public Servers and wait for the automatic refresh (every 10 seconds).
4. Closing the world removes its advertisement; stale results expire after 45 seconds.

Friends-only and invitation-only worlds are not published. The catalog uses the LAN player limit selected by the host (2–64), including the host. Lobby metadata is an untrusted advertisement, not permission to join. See [Steam catalog notes](docs/STEAM_DIRECTORY.md).

## Optional HTTP development backend

The client/server registry origin is supplied explicitly as a JVM property:

```text
-De4steam.publicServers.registryOrigin=https://registry.example
```

Publication remains disabled until enabled separately:

```text
-De4steam.publicServers.publishIntegrated=true
-De4steam.publicServers.publishDedicated=true
```

These flags concern only the optional HTTP backend, not default Steam discovery. Production HTTP attestation remains unavailable; no fake registry receipt is generated.

Plain HTTP is rejected. Loopback HTTP is available only for explicit local development with `-De4steam.publicServers.allowDevelopmentLoopback=true`.

## Build

```powershell
.\gradlew.bat verification --no-configuration-cache
.\gradlew.bat releaseArtifactAudit --no-configuration-cache
```

The second command builds, audits and collects all seven runtime JARs in `build/release`. The backend distribution is created with `:registry-server:backendArtifact`.

See [Architecture](docs/ARCHITECTURE.md), [Protocol](docs/REGISTRY_PROTOCOL.md), [Security](docs/SECURITY.md), [Privacy](docs/PRIVACY.md), [Development](docs/DEVELOPMENT.md), [Deployment](docs/DEPLOYMENT.md), and [Testing](docs/TESTING.md).

## Links

- [e4steam](https://github.com/Kamilhik/e4steam)
- [K2 Studio Development](https://t.me/K2Studio_Dev)
- [Discord](https://discord.gg/zFvrHz2ys7)
- [Donate](https://godonate.ru/@kamilchik)

<details>
<summary>Русский</summary>

# e4steam Public Servers

Экспериментальный аддон e4steam со списком общих миров прямо в игре. Начиная с beta.9 каталог работает через Steam, без VPS, домена и API-ключа.

Для `0.1.0-beta.10` нужно обновлённое ядро **e4steam 0.3.2-beta.1** с рабочим сервисом лобби. Обычный 0.3.2 не подходит. Версия Addon API остаётся **1.1.0**.

Это локальная сборка для проверки, а не релиз с подтверждённым multiplayer-тестом. Через Steam сейчас публикуются только миры игроков; выделенные серверы без клиента Steam этим механизмом пока не поддерживаются. Вкладка выделенных серверов будет пустой.

На обоих ПК установите новое ядро и аддон, запустите Steam. Хост выбирает **«Общий сервер»** в «Открыть для сети» и подтверждает публикацию; гость открывает каталог и ждёт автоматического обновления списка (каждые 10 секунд). Закрытые миры не публикуются. Лимит берётся из настроек LAN: от 2 до 64 игроков вместе с хостом. Все проверки подлинности и допуска при подключении сохраняются.

Для сборки выполните:

```powershell
.\gradlew.bat releaseArtifactAudit --no-configuration-cache
```

Готовые семь JAR-файлов появятся в `build/release`. Retro-сборки e4steam для Minecraft 1.7–1.16 пока не загружают Addon API и пользовательские режимы доступа; для них сначала потребуется отдельное обновление ядра.

Для Steam-каталога параметры запуска не нужны. Описанный выше HTTP backend оставлен только как отдельный экспериментальный путь; его production-проверка публикаций ещё не реализована.

</details>
