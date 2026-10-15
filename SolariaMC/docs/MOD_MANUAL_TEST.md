# Manual checks

## M1 foundation — awaiting human action

Automated config tests are **plumbing check (synthetic)**, not evidence of
classroom usability. No rendering or gameplay observation is claimed.

1. Run `gradlew.bat runClient` from the mod directory and enter a disposable world
   with cheats enabled. Confirm the mod loads and the existing wiki still works.
2. Run `/solaria` and `/solaria status`. Expect foundation readiness and an explicit
   notice that service health/scanning/building are not implemented yet.
3. Edit `run/config/solariamod.json`, set `blocks_per_tick` to 128, and run
   `/solaria config reload`. Expect success. Set it to 0 and reload; expect
   `[INPUT_INVALID]`, guidance, and retention of the previous valid snapshot.
   Restore 128 and reload; status should no longer show an error.
4. Test with a non-operator on a disposable dedicated server. Expect
   `[PERMISSION_DENIED]` for status/reload. As console/operator, change the teacher
   permission level and reload; confirm checks change immediately. Restore level 2.
5. Confirm Solaria log entries never contain config contents, names, paths, URLs,
   or exception messages; failures should log only a stable reason code.
6. Dedicated server startup requires the human to review and accept Minecraft's
   EULA in the server run directory. The agent does not accept it on your behalf.
   After acceptance, start with `gradlew.bat runServer --args="nogui"`, repeat the
   command checks from the console, then type `stop`. Use a disposable world.

Multiplayer reload affects server settings only. Restart the client to pick up
local client settings until client UI/service lifecycle support is implemented.