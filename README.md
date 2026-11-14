# NEI Build Planner

Client-side addon for NotEnoughItems in GTNH 2.9. The server needs nothing.

Built against NEI 2.8.171, BlockRenderer6343 1.4.25, StructureLib 1.4.47, GT5u 5.09.54.265,
NotEnoughEnergistics 1.7.50 and AE2 rv3-beta-1095.

## Features

| Action | Where | What it does |
| --- | --- | --- |
| **Alt + F** | Hover any item (item list, bookmarks, recipe page, preview candidates) | Toggles "preferred in multiblock previews". The preview uses preferred items wherever a position accepts them: hatches, busses, glass, coils. Earlier preferences win. |
| **B** button | Under the multiblock preview | Opens the build planner: scroll over a hatch (or click - / +, Shift = 10) to change its count; that dot group's casing count moves the other way. **Bookmark** adds the material list (controller, hatches, blocks) as a new bookmark group. |
| **Alt + A** | Hover an item | Adds a bookmark without NEI's toggle, so the same item can be bookmarked again. |
| **Alt + C** | Hover a bookmark group, in an AE2 terminal | Requests autocrafts for everything the group needs that is missing and has a pattern, one confirmation screen at a time (NEE's request chain). Cancel stops the chain. |
| **V / Shift + V** | Bookmark pulls | All or nothing: if storage can't cover every item, nothing is pulled and the missing items are listed in chat. |

The keys follow NEI's own bindings (bookmark, favorite, craft); rebinding those in Controls moves the Alt variants too.
Holding Alt shows them in NEI's tooltip hotkey list (Alt+C only over a bookmark group while an AE2 terminal is open).

## Config

`config/neibuildplanner.cfg`

- `pull.allOrNothing` (default `true`)
- `pull.missingChatMessage` (default `true`)

Preferred items are stored in `config/neibuildplanner-preferred.txt`, one item per line.

## Build

```bash
./gradlew build
```

The jar is in `build/libs/` (the one without `-dev` / `-sources`).
