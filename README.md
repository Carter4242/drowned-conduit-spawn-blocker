# DrownedConduitSpawnBlocker

Building underwater is often impossible because of the huge numbers of drowned that spawn, this plugin adds an easy, sensible, way to prevent that, as well as creating another use for conduits.

DrownedConduitSpawnBlocker is a simple Minecraft paper plugin for 1.20+ that prevents Drowned from spawning near placed conduits. Automatically tracks conduit placement/removal and saves their locations for efficient spawn prevention.

## Features

- Prevents drowned from spawning near conduits
- Configurable chunk radius for spawn checks
- Periodic autosave of conduit location data (can easily scale up to huge worlds)
- If `autosave-ticks` is 0 or below, conduit data is saved instantly (async) on every change

## Configuration

Edit `config.yml` in the plugin's folder:

- `autosave-ticks`: How often (in ticks) to autosave placed conduits (default: 6000; 0 or below to always instantly save (could cause problems if there are hundreds of conduits in the world))
- `chunk-check-radius`: How many chunks away to check for conduits (default: 2 = 5x5 chunk area around the conduit)
- `debug`: Enable debug logging for drowned spawn events (default: false)

If the server crashes, recently placed/removed conduits may be out of sync. Simply add/remove a conduit at the location to fix.
