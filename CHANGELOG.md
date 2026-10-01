# 3.1.0 — NeoForge 1.21.1

- Added permanent food history with `Miscellaneous.queueSize = 0`.
- Added `Advanced.decayEnabled` to disable contribution decay independently of history retention.
- Expanded meal counts, history length and decay positions to 2,147,483,647; increased the base contribution limit to 1,000,000.
- Kept reward tiers freely configurable and added detailed English tutorials and examples to generated config comments.
- Preserved old food-history saves, prevented large counter overflow and synchronized the new decay setting with clients.
- Optimized automatic food selection for permanent history without decay and repeated updates of large attribute reward lists.
- Added regression tests and reproducible performance benchmarks.

# 3.0.0 — NeoForge 1.21.1

- Ported by JIA from Vice's Spice of Life: Apple Pie Edition.
- Uses the `sollimepie` mod ID, resource namespace, and command prefix.
- Ported Spice of Life: Lime Pie Edition to Minecraft 1.21.1 and NeoForge.
- Updated food tracking, lunch containers, menus, recipes, and networking for 1.21.1.
- Fixed lunch container contents after slot mutations and missing food ID handling.
- Redrew the food book, lunch bag, lunchbox, golden lunchbox, and mod logo textures (#1).
- Completed Simplified Chinese translations and replaced hardcoded interface text.
- Added a creative mode tab and fixed repeated food book background blur.
- Fixed configuration hot reload and synchronized calculation settings with clients.
- Fixed configuration parsing and edge cases in lunch container operations.
- Cached food filtering and simulations, and avoided redundant attribute updates.
- Disabled legacy integrations that are incompatible with Minecraft 1.21.1.

Origins integration has not been verified with a compatible NeoForge 1.21.1 build.
