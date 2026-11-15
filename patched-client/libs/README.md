# lwjgl3-shim.jar

A shim-only extraction of Tessera's former `lwjgl3-agent.jar`: it contains
`org/lwjgl/**` (the LWJGL 2 API implemented on GLFW 3 / LWJGL 3) and
`tessera/lwjgl3/Bridge`, but no `Premain-Class`, no `TesseraLwjgl3Agent` and no
ASM.

The patcher merges these classes into `client.jar` and performs the LWJGL 2 →
LWJGL 3 call-site rewrite itself (`Lwjgl3Rewriter`). The shim is
GPL-3.0-or-later, like Tessera; the source lives in the Tessera history at
`crates/tessera-core/assets/lwjgl3-agent-src/`.
