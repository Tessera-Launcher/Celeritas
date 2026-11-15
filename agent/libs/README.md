# lwjgl3-agent.jar

This is Tessera's `crates/tessera-core/assets/lwjgl3-agent.jar`, copied so the
build can borrow its LWJGL2→LWJGL3 call rewriter without reimplementing the
rename table. The patcher applies that rewriter at build time and only copies
the shim classes (`org/lwjgl/**`, `tessera/lwjgl3/Bridge`) into the client jar;
`TesseraLwjgl3Agent` itself is not included.

It is GPL-3.0-or-later, like Tessera. The source lives in
`Tessera/crates/tessera-core/assets/lwjgl3-agent-src/`.
