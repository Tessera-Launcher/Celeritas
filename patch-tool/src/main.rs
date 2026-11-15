//! Generates and applies the `.tpatch` format used by Celeritas' patched
//! 1.8.9 client.
//!
//! The base is the **normalized** client jar (signatures and `Sealed:` already
//! removed), not the raw Mojang download. Every entry is verified by its
//! uncompressed SHA-256, so recompressing or re-timestamping the base jar does
//! not invalidate a patch.

use std::collections::BTreeMap;
use std::fs::{self, File};
use std::io::{Cursor, Read, Write};
use std::path::Path;

use qbsdiff::{Bspatch, Bsdiff};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipArchive, ZipWriter};

const FORMAT: u32 = 1;
const CANONICALIZATION: u32 = 1;

#[derive(Serialize, Deserialize, Debug)]
struct PatchManifest {
    format: u32,
    id: String,
    canonicalization: u32,
    base: BaseInfo,
    files: Vec<FileOp>,
}

#[derive(Serialize, Deserialize, Debug)]
struct BaseInfo {
    /// SHA-256 over the sorted `path\0sha256` listing of the normalized base.
    entries_sha256: String,
    /// Informational: the exact base jar this patch was generated from.
    jar_sha256: String,
    size: u64,
}

#[derive(Serialize, Deserialize, Debug)]
struct FileOp {
    path: String,
    op: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    encoding: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    blob: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    blob_size: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    target_size: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    target_sha256: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    base_sha256: Option<String>,
}

fn main() {
    if let Err(error) = run() {
        eprintln!("error: {error}");
        std::process::exit(1);
    }
}

fn run() -> Result<(), String> {
    let args: Vec<String> = std::env::args().collect();
    match args.get(1).map(String::as_str) {
        Some("generate") => {
            if args.len() < 5 {
                return Err("usage: generate <base.jar> <target.jar> <out.tpatch> [id]".into());
            }
            let id = args.get(5).map(String::as_str).unwrap_or("celeritas-1.8.9");
            generate(Path::new(&args[2]), Path::new(&args[3]), Path::new(&args[4]), id)
        }
        Some("apply") => {
            if args.len() != 5 {
                return Err("usage: apply <base.jar> <patch.tpatch> <out.jar>".into());
            }
            apply(Path::new(&args[2]), Path::new(&args[3]), Path::new(&args[4]))
        }
        Some("verify") => {
            if args.len() != 5 {
                return Err("usage: verify <base.jar> <patch.tpatch> <target.jar>".into());
            }
            verify(Path::new(&args[2]), Path::new(&args[3]), Path::new(&args[4]))
        }
        _ => Err("usage: celeritas-patch-tool <generate|apply|verify> ...".into()),
    }
}

fn generate(base_path: &Path, target_path: &Path, out_path: &Path, id: &str) -> Result<(), String> {
    let base = read_jar(base_path)?;
    let target = read_jar(target_path)?;
    let base_digest = entries_digest(&base);
    let mut blobs: BTreeMap<String, Vec<u8>> = BTreeMap::new();
    let mut files = Vec::new();
    let mut reconstructed = base.clone();

    for (path, target_bytes) in &target {
        match base.get(path) {
            Some(base_bytes) if base_bytes == target_bytes => continue,
            Some(base_bytes) => {
                let delta = bsdiff(base_bytes, target_bytes)?;
                let (encoding, payload) = if delta.len() < target_bytes.len() {
                    ("qbsdiff", delta)
                } else {
                    ("raw", target_bytes.clone())
                };
                let blob = sha256_hex(&payload);
                blobs.entry(blob.clone()).or_insert(payload);
                files.push(FileOp {
                    path: path.clone(),
                    op: "put".into(),
                    encoding: Some(encoding.into()),
                    blob: Some(blob),
                    blob_size: None,
                    target_size: Some(target_bytes.len() as u64),
                    target_sha256: Some(sha256_hex(target_bytes)),
                    base_sha256: Some(sha256_hex(base_bytes)),
                });
                reconstructed.insert(path.clone(), target_bytes.clone());
            }
            None => {
                let blob = sha256_hex(target_bytes);
                blobs.entry(blob.clone()).or_insert_with(|| target_bytes.clone());
                files.push(FileOp {
                    path: path.clone(),
                    op: "put".into(),
                    encoding: Some("raw".into()),
                    blob: Some(blob),
                    blob_size: None,
                    target_size: Some(target_bytes.len() as u64),
                    target_sha256: Some(sha256_hex(target_bytes)),
                    base_sha256: None,
                });
                reconstructed.insert(path.clone(), target_bytes.clone());
            }
        }
    }
    for (path, base_bytes) in &base {
        if !target.contains_key(path) {
            files.push(FileOp {
                path: path.clone(),
                op: "delete".into(),
                encoding: None,
                blob: None,
                blob_size: None,
                target_size: None,
                target_sha256: None,
                base_sha256: Some(sha256_hex(base_bytes)),
            });
            reconstructed.remove(path);
        }
    }
    files.sort_by(|a, b| a.path.cmp(&b.path));

    // The in-memory reconstruction must reproduce the target exactly before a
    // patch is allowed out of the tool.
    if reconstructed != target {
        return Err("internal error: reconstructed target does not match the input".into());
    }

    let manifest = PatchManifest {
        format: FORMAT,
        id: id.to_string(),
        canonicalization: CANONICALIZATION,
        base: BaseInfo {
            entries_sha256: base_digest,
            jar_sha256: sha256_hex(&fs::read(base_path).map_err(|error| error.to_string())?),
            size: fs::metadata(base_path).map_err(|error| error.to_string())?.len(),
        },
        files,
    };
    let body = toml::to_string_pretty(&manifest).map_err(|error| error.to_string())?;

    write_patch(out_path, &body, &blobs)?;
    println!(
        "patch {}: {} files, {} blobs, {} bytes",
        out_path.display(),
        manifest.files.len(),
        blobs.len(),
        fs::metadata(out_path).map(|meta| meta.len()).unwrap_or(0)
    );
    Ok(())
}

fn apply(base_path: &Path, patch_path: &Path, out_path: &Path) -> Result<(), String> {
    let base = read_jar(base_path)?;
    let (manifest, blobs) = read_patch(patch_path)?;
    if manifest.format != FORMAT {
        return Err(format!("unsupported patch format {}", manifest.format));
    }
    if entries_digest(&base) != manifest.base.entries_sha256 {
        return Err("base jar does not match the patch's normalized base".into());
    }
    let target = reconstruct(&base, &manifest, &blobs)?;
    write_jar(out_path, &target)?;
    println!(
        "applied {} to {}: {} entries",
        patch_path.display(),
        out_path.display(),
        target.len()
    );
    Ok(())
}

fn verify(base_path: &Path, patch_path: &Path, target_path: &Path) -> Result<(), String> {
    let base = read_jar(base_path)?;
    let (manifest, blobs) = read_patch(patch_path)?;
    if entries_digest(&base) != manifest.base.entries_sha256 {
        return Err("base jar does not match the patch's normalized base".into());
    }
    let reconstructed = reconstruct(&base, &manifest, &blobs)?;
    let target = read_jar(target_path)?;
    if reconstructed != target {
        return Err("reconstructed jar does not match the supplied target".into());
    }
    println!("verified {} entries", target.len());
    Ok(())
}

fn reconstruct(
    base: &BTreeMap<String, Vec<u8>>,
    manifest: &PatchManifest,
    blobs: &BTreeMap<String, Vec<u8>>,
) -> Result<BTreeMap<String, Vec<u8>>, String> {
    let mut result = base.clone();
    for file in &manifest.files {
        safe_path(&file.path)?;
        match file.op.as_str() {
            "delete" => {
                let Some(base_bytes) = base.get(&file.path) else {
                    return Err(format!("delete of missing entry {}", file.path));
                };
                verify_entry(&file.path, base_bytes, file.base_sha256.as_deref(), None)?;
                result.remove(&file.path);
            }
            "put" => {
                let blob_id = file.blob.as_ref().ok_or_else(|| format!("{} has no blob", file.path))?;
                let payload = blobs
                    .get(blob_id)
                    .ok_or_else(|| format!("missing blob {blob_id} for {}", file.path))?;
                let bytes = match file.encoding.as_deref().unwrap_or("raw") {
                    "raw" => payload.clone(),
                    "qbsdiff" => {
                        let base_bytes = base
                            .get(&file.path)
                            .ok_or_else(|| format!("qbsdiff entry {} has no base", file.path))?;
                        verify_entry(&file.path, base_bytes, file.base_sha256.as_deref(), None)?;
                        let patcher = Bspatch::new(payload).map_err(|error| error.to_string())?;
                        let mut out = Vec::with_capacity(patcher.hint_target_size() as usize);
                        patcher
                            .apply(base_bytes, Cursor::new(&mut out))
                            .map_err(|error| error.to_string())?;
                        out
                    }
                    other => return Err(format!("unknown encoding {other} for {}", file.path)),
                };
                verify_entry(&file.path, &bytes, None, file.target_sha256.as_deref())?;
                if let Some(size) = file.target_size {
                    if bytes.len() as u64 != size {
                        return Err(format!("size mismatch for {}", file.path));
                    }
                }
                result.insert(file.path.clone(), bytes);
            }
            other => return Err(format!("unknown operation {other} for {}", file.path)),
        }
    }
    Ok(result)
}

fn verify_entry(
    path: &str,
    bytes: &[u8],
    expected: Option<&str>,
    target: Option<&str>,
) -> Result<(), String> {
    let actual = sha256_hex(bytes);
    if let Some(expected) = expected {
        if actual != expected {
            return Err(format!("base hash mismatch for {path}"));
        }
    }
    if let Some(target) = target {
        if actual != target {
            return Err(format!("target hash mismatch for {path}"));
        }
    }
    Ok(())
}

fn bsdiff(base: &[u8], target: &[u8]) -> Result<Vec<u8>, String> {
    let mut patch = Vec::new();
    Bsdiff::new(base, target)
        .compare(Cursor::new(&mut patch))
        .map_err(|error| error.to_string())?;
    Ok(patch)
}

fn read_jar(path: &Path) -> Result<BTreeMap<String, Vec<u8>>, String> {
    let file = File::open(path).map_err(|error| format!("{}: {error}", path.display()))?;
    let mut archive = ZipArchive::new(file).map_err(|error| format!("{}: {error}", path.display()))?;
    let mut entries = BTreeMap::new();
    for index in 0..archive.len() {
        let mut entry = archive
            .by_index(index)
            .map_err(|error| format!("{}: {error}", path.display()))?;
        if entry.is_dir() {
            continue;
        }
        let name = entry
            .name()
            .map_err(|error| format!("{}: {error}", path.display()))?
            .to_string();
        safe_path(&name)?;
        let mut bytes = Vec::new();
        entry
            .read_to_end(&mut bytes)
            .map_err(|error| format!("{}: {error}", name))?;
        entries.insert(name, bytes);
    }
    Ok(entries)
}

fn write_jar(path: &Path, entries: &BTreeMap<String, Vec<u8>>) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let tmp = path.with_extension("tmp");
    let file = File::create(&tmp).map_err(|error| error.to_string())?;
    let mut zip = ZipWriter::new(file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    for (name, bytes) in entries {
        zip.start_file(name, options).map_err(|error| error.to_string())?;
        zip.write_all(bytes).map_err(|error| error.to_string())?;
    }
    zip.finish().map_err(|error| error.to_string())?;
    fs::rename(&tmp, path).map_err(|error| error.to_string())
}

fn write_patch(
    path: &Path,
    manifest: &str,
    blobs: &BTreeMap<String, Vec<u8>>,
) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let tmp = path.with_extension("tmp");
    let file = File::create(&tmp).map_err(|error| error.to_string())?;
    let mut zip = ZipWriter::new(file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    zip.start_file("patch.toml", options)
        .map_err(|error| error.to_string())?;
    zip.write_all(manifest.as_bytes())
        .map_err(|error| error.to_string())?;
    for (blob, bytes) in blobs {
        zip.start_file(format!("blobs/{blob}"), options)
            .map_err(|error| error.to_string())?;
        zip.write_all(bytes).map_err(|error| error.to_string())?;
    }
    zip.finish().map_err(|error| error.to_string())?;
    fs::rename(&tmp, path).map_err(|error| error.to_string())
}

fn read_patch(
    path: &Path,
) -> Result<(PatchManifest, BTreeMap<String, Vec<u8>>), String> {
    let file = File::open(path).map_err(|error| format!("{}: {error}", path.display()))?;
    let mut archive = ZipArchive::new(file).map_err(|error| format!("{}: {error}", path.display()))?;
    let mut manifest = None;
    let mut blobs = BTreeMap::new();
    for index in 0..archive.len() {
        let mut entry = archive
            .by_index(index)
            .map_err(|error| format!("{}: {error}", path.display()))?;
        if entry.is_dir() {
            continue;
        }
        let name = entry
            .name()
            .map_err(|error| format!("{}: {error}", path.display()))?
            .to_string();
        let mut bytes = Vec::new();
        entry
            .read_to_end(&mut bytes)
            .map_err(|error| format!("{}: {error}", name))?;
        if name == "patch.toml" {
            manifest = Some(
                toml::from_str::<PatchManifest>(
                    std::str::from_utf8(&bytes).map_err(|error| error.to_string())?,
                )
                .map_err(|error| error.to_string())?,
            );
        } else if let Some(blob) = name.strip_prefix("blobs/") {
            blobs.insert(blob.to_string(), bytes);
        }
    }
    let manifest = manifest.ok_or_else(|| "patch has no patch.toml".to_string())?;
    Ok((manifest, blobs))
}

fn entries_digest(entries: &BTreeMap<String, Vec<u8>>) -> String {
    let mut digest = Sha256::new();
    for (path, bytes) in entries {
        digest.update(path.as_bytes());
        digest.update([0]);
        digest.update(sha256_hex(bytes).as_bytes());
        digest.update(b"\n");
    }
    hex::encode(digest.finalize())
}

fn sha256_hex(bytes: &[u8]) -> String {
    hex::encode(Sha256::digest(bytes))
}

fn safe_path(name: &str) -> Result<(), String> {
    if name.is_empty()
        || name.starts_with('/')
        || name.starts_with('\\')
        || name.contains("../")
        || name.contains("..\\")
        || name == ".."
        || name.contains(':')
    {
        return Err(format!("unsafe jar entry {name:?}"));
    }
    Ok(())
}
