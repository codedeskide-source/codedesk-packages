# Towns Community Completion build workspace

This branch is intentionally isolated from `main`. It builds an unofficial community-completion edition of Towns from the public GPLv3 OpenTowns source while leaving the proprietary Towns graphics/audio/fonts out of the repository and release artifact.

The Windows build imports those media assets from the player's owned Towns installation on first run using OpenTowns' existing launcher.

## Completion scope

The build keeps the unreleased original v15 work already preserved by OpenTowns, restores the unfinished Gods subsystem and its save/event hooks, adds hero personal gold and enemy loot, turns existing weapon/armor cabinets into hero stores, and adds player-posted hero hunt bounties/rewards. Existing hero friendships/parties, exploration, dungeons, caravans, events and bury systems remain intact.

`apply_completion.py` is pinned to a known OpenTowns revision by the workflow. The workflow applies the patch, runs the automated tests, packages a self-contained Windows app image, and publishes both the player ZIP and the corresponding source/patch artifacts.
