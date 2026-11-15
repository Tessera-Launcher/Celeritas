# celeritas-patch-tool

Generates and applies the `.tpatch` format used for Celeritas' 1.8.9 patched
client. The base is the **normalized** `client.jar` (signatures removed,
manifest unsealed), never the raw Mojang download.

## Files

```text
client-base.jar   normalized vanilla client (the patch base)
client.jar        patched client
client.tpatch     patch from client-base.jar -> client.jar
```

The patch is a ZIP containing `patch.toml` and content-addressed
`blobs/<sha256>` entries. Unchanged files are not listed: the applier copies
them from the base. Changed/new files are raw or `qbsdiff` deltas, whichever is
smaller. Every base entry is covered by `base.entries_sha256` (a digest over the
sorted `path\0sha256` listing), so recompressing or re-timestamping the base jar
does not invalidate it.

## Commands

```sh
cargo build --release

# generate from an already-normalized base
target/release/celeritas-patch-tool generate \
  client-base.jar client.jar client.tpatch celeritas-1.8.9

# apply and verify
target/release/celeritas-patch-tool apply \
  client-base.jar client.tpatch out.jar
target/release/celeritas-patch-tool verify \
  client-base.jar client.tpatch client.jar
```

`generate` reconstructs the target in memory before writing the patch and
refuses to emit a patch that does not reproduce it exactly.

## Format notes

- `format = 1`, `canonicalization = 1`
- Paths are ZIP entry names; absolute paths, `..` and drive prefixes are
  rejected.
- `base.entries_sha256` is the authoritative base check.
- `base.jar_sha256`/`base.size` are informational only.
- Operations: `put` (`encoding = "raw" | "qbsdiff"`), `delete`.
- `target_sha256`/`target_size` are checked after reconstruction.
