# Changelog

All notable changes to e4steam are documented here. Version numbers below
belong to this fork and are independent of upstream e4mc releases.

## 0.3.0-beta.1 - 2026-08-03

### English

- Replaced the deprecated `ISteamNetworking` transport with
  `ISteamNetworkingMessages`, the packet-oriented API built on Steam
  Networking Sockets; raised the wire and lobby protocol to version 3.
- Added periodic acceptance for authenticated lobby peers and Steam Networking
  Sockets diagnostics for delayed session-request callbacks.
- Added offline-mode profile compatibility: access remains authenticated by
  Steam ID and invite token, while Minecraft 26.2 friend notifications use
  vanilla deterministic offline UUIDs without Mojang profile resolution.
- Added an experimental native-style Steam Friends screen with compact menu
  buttons, Friends and Invitations tabs, responsive pagination, Steam avatars,
  online/e4steam/Spacewar states, tooltips, empty/loading/error states, and
  direct Invite, Join, Profile, Refresh, and Back actions.
- Split Steam social data from lobby management and pass immutable snapshots
  to Minecraft's client thread; Friends UI actions now reuse the Steam worker
  without creating per-click background threads.
- Added expiring and cancelable invitation history, duplicate-click protection,
  safe screen reopen/close handling, rich-presence compatibility checks, and
  overlay fallbacks.
- Kept App ID 480, TCP and UDP tunneling, voice-mod compatibility, the standard
  8-player integrated-world limit, and the Minecraft 26.2 terrain-loading fix.
- Added Friends UI lifecycle, social sorting, invitation expiry, capacity, and
  release-JAR compatibility audits.
- Made optional menu, Friends UI, LAN-option, and restored-command hooks
  fail-open for modpacks: a conflicting UI/command mod now disables only the
  affected e4steam integration and records one warning instead of crashing the
  client. Core Steam TCP/UDP hooks remain strict and auditable.
- Fixed intermittent invalid-session disconnects when Steam reported a private
  lobby join to the guest before the host saw its member update. Client bridges
  now wait for `OPEN_ACK` before forwarding Minecraft traffic and retry the
  authenticated handshake for a bounded time without weakening invite-only
  membership or token checks.
- Rebuilt the Minecraft 26.x Steam Friends overlay around the original
  220-pixel vanilla layout: 110-pixel tabs, 204x28 entries, native panel blur,
  list margins, scrollbar, empty illustration, loading animation, and
  highlighted accept/reject sprites. Steam search and filtering remain in the
  Friends tab without a non-vanilla refresh button.
- Ported that original Friends overlay layout and its complete vanilla sprite
  set to every supported Minecraft generation, including the tabs, checkbox,
  action states, empty-list illustration, separators, and scrollbar.

### Русский

- Устаревший транспорт `ISteamNetworking` заменён на
  `ISteamNetworkingMessages` — пакетный API поверх Steam Networking Sockets;
  версия сетевого и lobby-протокола повышена до 3.
- Добавлены периодическое принятие сессий проверенных участников lobby и
  диагностика Steam Networking Sockets при задержке callback-запроса.
- Добавлена совместимость с offline-mode: доступ по-прежнему проверяется через
  Steam ID и токен приглашения, а уведомления Minecraft 26.2 используют
  стандартные offline UUID без обращения к профилям Mojang.
- Добавлен экспериментальный нативный экран друзей Steam: компактные кнопки,
  вкладки «Друзья» и «Приглашения», адаптивная пагинация, аватары Steam,
  статусы сети/e4steam/Spacewar, подсказки, состояния загрузки, пустого списка
  и ошибки, а также действия приглашения, подключения, профиля, обновления и
  возврата.
- Данные Steam Social отделены от управления лобби и передаются в клиентский
  поток Minecraft неизменяемыми снимками; интерфейс использует общий Steam
  worker без создания отдельного фонового потока на каждое нажатие.
- Добавлена история приглашений со сроком действия и отменой, защита от быстрых
  повторных нажатий, безопасное повторное открытие экрана, проверка совместимости
  rich presence и запасной путь через оверлей Steam.
- Сохранены App ID 480, TCP и UDP, поддержка голосовых модов, стандартный лимит
  интегрированного мира 8 игроков и исправление загрузки территории Minecraft 26.2.
- Добавлены тесты жизненного цикла Friends UI, сортировки, срока приглашений,
  вместимости и аудит совместимости релизных JAR.
- Необязательные хуки меню, Friends UI, настроек LAN и восстановленных команд
  переведены в безопасный режим для модпаков: при конфликте отключается только
  затронутая интеграция e4steam и один раз записывается предупреждение вместо
  вылета клиента. Основные хуки Steam TCP/UDP остаются строгими и проверяемыми.
- Исправлены редкие отключения с сообщением о недействительной сессии, когда
  Steam сообщал гостю о входе в приватное lobby раньше, чем хост видел нового
  участника. Клиентский мост теперь ждёт `OPEN_ACK` перед передачей трафика
  Minecraft и ограниченно повторяет защищённое рукопожатие, не ослабляя проверку
  членства invite-only lobby и токена приглашения.
- Экран друзей Steam для Minecraft 26.x перестроен по оригинальной ванильной
  разметке шириной 220 пикселей: вкладки по 110 пикселей, строки 204x28,
  штатное размытие фона, отступы списка, полоса прокрутки, иллюстрация пустого
  списка, анимация загрузки и hover-спрайты принятия/отклонения. Поиск и фильтр
  Steam сохранены без лишней кнопки обновления.

- Оригинальная разметка Friends overlay и полный набор ванильных спрайтов
  перенесены на все поддерживаемые поколения Minecraft: вкладки, галочка,
  состояния кнопок, иллюстрация пустого списка, разделители и полоса прокрутки.

## 0.2.0 - 2026-08-01

### English

- Verified 99 Windows client launches across Fabric, Quilt, Forge, and NeoForge.
- Fixed modern Fabric API metadata for Minecraft 26.x.
- Fixed Forge 1.18.2 startup by removing an invalid inherited `PauseScreen.tick` injection.
- Spacewar now closes when Minecraft connects to a regular server.
- Replaced launcher-specific wording with generic Minecraft launcher guidance.
- Promoted e4steam to its first stable public release while keeping Steam App
  ID 480 as the permanent transport namespace.
- Removed obsolete alpha releases and normalized the stable release metadata.
- Documented installation, Steam Overlay setup, troubleshooting, supported
  files, platform limits, and the verified compatibility matrix.
- Added testable Steam lifecycle boundaries and regression coverage for
  restart, cancellation, invalid or expired invitations, unknown peers, queue
  overflow, Steam loss, world shutdown, lobby loss, and concurrent guests.
- Split Steam runtime responsibilities into lifecycle, packet transport,
  bridge registry, outbound queue, and lobby management components without
  changing the wire protocol.
- Updated the release, security, contribution, and bug-report documentation
  for the stable 0.2.0 line.
- Separated client-launch evidence from manual host/guest multiplayer evidence
  and added a native Windows build job to GitHub Actions.
- Documented that every loader/version JAR is shared by Windows x64 and Linux
  x64 and bundles native Steam libraries for both systems.

### Русский

- Проверен запуск 99 клиентов Windows на Fabric, Quilt, Forge и NeoForge.
- Исправлена зависимость Fabric API для Minecraft 26.x.
- Исправлен запуск Forge 1.18.2: удалено некорректное внедрение в унаследованный
  метод `PauseScreen.tick`.
- Spacewar теперь закрывается при подключении Minecraft к обычному серверу.
- Убраны упоминания конкретного лаунчера; подсказки подходят для любого
  лаунчера Minecraft.
- e4steam выпущен как первый стабильный релиз. Steam App ID 480 остаётся
  постоянным транспортным идентификатором проекта.
- Удалены устаревшие альфа-релизы и приведены в порядок данные стабильного релиза.
- Добавлены инструкции по установке, настройке оверлея Steam, устранению проблем,
  выбору файла и ограничениям платформ.
- Добавлены тесты перезапуска Steam, отмены подключения, неверных и просроченных
  приглашений, незнакомых пользователей, переполнения очереди, отключения Steam,
  закрытия мира, потери лобби и одновременных гостей.
- SteamRuntime разделён на компоненты жизненного цикла, транспорта пакетов,
  реестра соединений, очереди отправки и управления лобби без изменения протокола.
- Документы выпуска, безопасности, участия в разработке и сообщения об ошибках
  приведены к состоянию стабильной ветки 0.2.0.
- Проверки запуска клиента отделены от ручных host/guest-проверок, а в GitHub
  Actions добавлена отдельная сборка на Windows.
- Уточнено, что один JAR для выбранной версии и загрузчика используется на
  Windows x64 и Linux x64 и содержит Steam-библиотеки для обеих систем.

## 0.2.0-alpha.4 - 2026-08-01

- Added an activity-scoped UDP tunnel alongside the existing Minecraft TCP
  bridge, enabling voice chat and other UDP-based mods.
- Added automatic runtime port discovery for Simple Voice Chat and automatic
  Minecraft-port mapping for Plasmo Voice. The selected UDP endpoint is sent
  to guests during the Steam handshake.
- Voice datagrams use Steam's unreliable no-delay delivery and a separate
  bounded queue so voice traffic cannot starve the Minecraft connection.
- Added local UDP proxy tests, protocol validation, a configurable fallback
  `voiceChatPort`, and six-artifact UDP audits.
- Raised the e4steam wire and lobby protocol version to 2; both players must
  use the same `0.2.0-alpha.4` build.

## 0.2.0-alpha.2 - 2026-07-31

- Removed the direct pre-1.21 `GenericDirtMessageScreen` link and select the
  renamed 1.21+ `GenericMessageScreen` through the compatibility boundary.
- Corrected Fabric compatibility: the Command API v1 build now covers
  Minecraft 1.17–1.18.2, while the Command API v2 build starts at 1.19.
- Added an artifact audit that rejects a direct link to the renamed screen.

## 0.2.0-alpha.1 - 2026-07-31

- Renamed the separate project, mod ID, Java namespace, commands, and release
  artifacts to **e4steam**.
- Added public-repository contribution guidance, issue/PR templates, and
  Dependabot configuration.
- Included the complete Apache License 2.0 text required by the shaded Kaleido
  Config dependency in the third-party notices packaged with the mod.
- Added six release variants: separate experimental Fabric/Quilt and Forge
  legacy artifacts for 1.17.x and 1.17.1–1.18.1 respectively, Fabric/Quilt for
  1.18–1.21.11, Fabric/Quilt Modern for 26.1–26.2, Forge for
  1.18.2–1.20.2, and NeoForge for 1.20.2–26.2. Wider compatibility remains
  gated on per-version smoke tests.
- Shortened new connection addresses to the
  `s-<SteamID-in-base36>-<token-in-base36>.steam` form.
- Added runtime Minecraft-version discovery and compatibility adapters for
  buttons, tooltips, multiplayer connection, and world disconnect across the
  declared version families.

## 0.1.0-alpha.3 - 2026-07-30

- Added Steam friends-only and invitation-only lobby modes to Minecraft's Open to LAN screen.
- Added Shift+Tab invitation support through Steam lobbies and rich presence.
- Added a Steam friends button to Multiplayer and an invitation button to the pause menu.
- Added `/e4steam invite` and a clickable invitation action in the host chat message.
- Added a random 128-bit invitation check and direct host friendship check for every incoming bridge; invitation-only sessions also require current private-lobby membership.
- Made Steamworks restartable and activity-scoped: App ID 480 is inactive during ordinary Minecraft use and shuts down after hosting, waiting, or playing ends.
- Kept a local-only LAN mode that never initializes Steamworks.

## 0.1.0-alpha.2 - 2026-07-30

- Fixed Steam native library loading from isolated NeoForge, Forge, and Fabric mod class loaders.
- Added verified extraction of the bundled Windows/Linux x64 Steam libraries to a content-addressed local cache.
- Added detailed native loading errors instead of the previous generic initialization message.

## 0.1.0-alpha.1 - 2026-07-30

- Replaced the original public relay transport with a Steam P2P bridge.
- Added development initialization through App ID 480 (Spacewar) without launching the Spacewar game.
- Added authenticated host addresses for direct Steam connections.
- Added direct Steam P2P transport with Valve relay fallback.
- Required the mod and a signed-in Steam client on both host and guest.
- Targeted Windows x64 and Linux x64 for the first release.
- Limited the first release to Minecraft's integrated single-player server; dedicated servers are not yet supported.
- Documented that the legacy `ISteamNetworking` API is deprecated and should be replaced by Steam Networking Sockets in a future release.
- Added English and Russian in-game messages for the Steam-based flow.
- Added protocol tests, bounded queues, generation-safe terminal frames, graceful half-close handling, Steam send-queue draining, and redacted invite logging.
- Added a runtime check that refuses to continue unless Steam actually initializes the process as App ID 480.

## Upstream history

This repository is derived from e4mc by skyevg and contributors. The original project's release history predates this separate Steam fork and is intentionally not reused as this project's changelog.
