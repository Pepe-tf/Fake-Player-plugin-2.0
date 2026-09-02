# Economy / Bot Rental

Players can pay real economy currency to rent a bot for a fixed number of hours - the bot
auto-despawns the moment its paid time runs out. Disabled by default.

## Overview

- **Provider-agnostic.** FPP talks to whichever economy plugin is actually installed:
  [Vault](https://github.com/milkbowl/Vault), [ExcellentEconomy](https://nightexpressdev.com/excellenteconomy/),
  or a Vault-compatible reimplementation like [Vault2.0](https://github.com/shalom25/Vault2.0) - see
  [Supported Economy Plugins](#supported-economy-plugins) below for how the detection actually works.
- **Billed per hour.** `/fpp rent buy <hours>` spawns a new rented bot; `/fpp rent extend <bot> <hours>`
  adds more time to one you already have.
- **Dynamic - works with your own shop plugin too.** `/fpp rent give <player> <bot|--new> <hours>`
  grants rental time **without touching any economy plugin at all**. Point your own shop GUI
  (ShopGUIPlus, EconomyShopGUI, zShop, …) at this as a console reward command and it charges the
  player however *it* likes - FPP's own economy integration is entirely optional for this path.
- **Never permanently loses a bot's data.** A rented bot despawning on expiry goes through the exact
  same despawn/inventory-save path as `/fpp despawn` - nothing special or destructive happens.

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/fpp rent buy <hours>` | Spawn a new rented bot for `<hours>` hours, charging your balance | `fpp.rent` |
| `/fpp rent extend <bot> <hours>` | Add `<hours>` more hours to a bot you own, charging your balance | `fpp.rent` |
| `/fpp rent info [bot]` | Show remaining time (all your rented bots, or one) | `fpp.rent.info` |
| `/fpp rent give <player> <bot\|--new> <hours>` | Grant hours without charging - admin/console/shop-plugin entry point | `fpp.rent.give` |
| `/fpp rent clear <bot>` | Remove a bot's rental - it becomes permanent again | `fpp.rent.give` |

`fpp.rent` and `fpp.rent.info` are included in `fpp.use` (the default everyone-tier permission) - any
player can attempt a purchase, gated purely by actually having the money and `economy.enabled: true`.

## Setup

```yaml
# plugins/FakePlayerPlugin/config.yml
economy:
  enabled: false          # turn on to allow real purchases via /fpp rent buy/extend
  provider: auto          # auto | vault | excellenteconomy | none
  excellent-economy-currency-id: money   # only used when the resolved provider is ExcellentEconomy
  rental:
    price-per-hour: 100.0
    price-per-bot-slot: 0.0     # one-time extra charge for a brand-new rented bot
    min-hours: 1
    max-hours: 72
    max-banked-hours: 168       # hard cap on time a bot can have banked across extensions
    warn-minutes-before-expiry: 10
    sweep-interval-seconds: 30
    max-bots-per-player: 3      # rented bots only; fpp.rent.unlimited bypasses this
```

`economy.enabled: false` (the default) only disables the self-service `buy`/`extend` purchase path -
`/fpp rent give` still works with no economy plugin involved at all, since it never touches money.

## Supported Economy Plugins

FPP resolves a working economy backend in this order (`economy.provider: auto`, the default):

1. **Vault's `Economy` service**, via Bukkit's own `ServicesManager` - the same lookup virtually every
   economy-integrated plugin uses. This one integration transparently covers:
   - **Real [Vault](https://github.com/milkbowl/Vault)** with any Vault-compatible economy plugin
     (EssentialsX, CMI, …) behind it.
   - **[Vault2.0](https://github.com/shalom25/Vault2.0)** - inspected directly: it registers itself
     under the literal plugin name `"Vault"` and its own economy class implements the exact same
     `net.milkbowl.vault.economy.Economy` interface real Vault does. There's nothing to distinguish
     it from real Vault at the API level, so it's covered automatically with zero extra code.
   - **[ExcellentEconomy](https://nightexpressdev.com/excellenteconomy/)** *when Vault is also
     installed* - its own documentation states it "works right out of the box with Vault."
2. **ExcellentEconomy's native API** (`su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI`, also
   via `ServicesManager`) - used when ExcellentEconomy is installed **without** Vault present.
   ExcellentEconomy is a multi-currency plugin with no single fixed default currency, so
   `economy.excellent-economy-currency-id` must name a currency you've actually created in it.
3. Otherwise, purchasing is unavailable (`/fpp rent buy`/`extend` report "no economy plugin
   available") - `/fpp rent give` is unaffected.

Set `economy.provider` to `vault` or `excellenteconomy` to pin one specific backend instead of
auto-detecting.

All of this is soft-dependency reflection under the hood (`Vault`/`ExcellentEconomy` are optional -
FPP works fine with neither installed), the same defensive style used for every other optional
integration in this plugin.

## Custom Shop Plugin Integration

Since `/fpp rent give` never touches an economy plugin itself, it's the integration point for
building your own shop with a completely different plugin (a GUI shop, a `/buy` command shop, a
crate/key system, whatever you already use). Set its reward/command action to:

```
fpp rent give %player% --new 4
```

or, to add time to a specific existing bot instead of spawning a new one:

```
fpp rent give %player% <bot-name> 4
```

Run this as console (`fpp.rent.give` defaults to `op`); most shop plugins support exactly this
"execute console command as reward" pattern. FPP charges nothing itself here - your shop plugin's own
economy hook (which might not even be Vault) handles the payment; this command only applies the
resulting bot/time.

## Notification & Expiry

- A rented bot's owner gets a one-time warning message `economy.rental.warn-minutes-before-expiry`
  minutes before time runs out (10 by default; set to `0` to disable).
- When time actually reaches zero, the bot despawns (same safe despawn path as `/fpp despawn`) and the
  owner is notified if online.
- `fpp.rent.unlimited` makes a player's rented bots never expire from time running out - useful for
  staff/VIP bots that should never be affected by the rental system at all.
- Rental time survives server restarts (persisted the same way every other bot setting is, to YAML
  and the database).

## Settings GUI

Each bot's settings GUI (`⚙ ɢᴇɴᴇʀᴀʟ` category) has a **ʀᴇɴᴛᴀʟ ᴛɪᴍᴇ** tile showing remaining time (or
`ᴘᴇʀᴍᴀɴᴇɴᴛ` for a non-rented bot) - click it to buy more hours the same way `/fpp rent extend` does.

## Placeholders

See [Placeholders](Placeholders) - `%fpp_rental_count%` and `%fpp_rental_remaining%`.
