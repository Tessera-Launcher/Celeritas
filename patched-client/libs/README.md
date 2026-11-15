# lwjgl3-shim.jar

A shim-only extraction of Tessera's former `lwjgl3-agent.jar`: it contains
`org/lwjgl/**` (the LWJGL 2 API implemented on GLFW 3 / LWJGL 3) and
`tessera/lwjgl3/Bridge`, but no `Premain-Class`, no `TesseraLwjgl3Agent` and no
ASM.

The patcher merges these classes into `client.jar` and performs the LWJGL 2 →
LWJGL 3 call-site rewrite itself (`Lwjgl3Rewriter`). The shim is
GPL-3.0-or-later, like Tessera; the source lives in the Tessera history at
`crates/tessera-core/assets/lwjgl3-agent-src/`.

## Local change

`libs/lwjgl3-shim.jar` carries one patch on top of the Tessera source: the
mouse shim now queues cursor movement as an event (button `-1`), as LWJGL 2
does. Vanilla only re-runs `GuiScreen.handleMouseInput()` for events that come
out of `Mouse.next()`, and it dispatches `mouseDragged` from the movement
events, so without them no slider or list scrollbar can be dragged. The diff is
in `libs/lwjgl3-shim.patch`.
