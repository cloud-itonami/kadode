(ns kadode.methods.cid
  "kadode 門出 — kotoba IPFS content-address (CIDv1, raw, sha2-256, base32).
  1:1 Clojure port of `methods/cid.py` (ADR-2606112238; the same family as rasen/hinagata cid.py).

  Reproduces the repo-canonical content-address used by the WASM loaders
  (20-actors/*/wasm/verify.mjs, ADR-2605231525 / 2606014500): CIDv1, raw codec (0x55),
  multihash sha2-256 (0x12 0x20), multibase base32-lower with the 'b' prefix. This is the SAME
  CID `ipfs add --cid-version=1 --raw-leaves` produces for a single raw block (< 256 KiB), so a
  kadode-drafted document's content-address is verifiable with or without the `ipfs` daemon.

  Single-block only by design: an individual resignation document fits one raw block.

  House style: pure fns; the only host edge is sha2-256 (`java.security.MessageDigest \"SHA-256\"`
  at the #?(:clj) edge — the JVM stdlib ships SHA-256, so no hand-port needed, unlike shomei's
  BLAKE2b). Bytes are unsigned-int 0..255 throughout (a JVM `byte` is signed, so we mask & 0xff).")

;; RFC4648 base32 lower, no padding (multibase 'b'); mirrors cid.py's _B32 string exactly.
(def ^:private b32 "abcdefghijklmnopqrstuvwxyz234567")

(defn- drain
  "Emit all complete 5-bit groups buffered in [val bits] into sb; return [val' bits']."
  [^StringBuilder sb val bits]
  (loop [val val, bits bits]
    (if (>= bits 5)
      (do
        (.append sb (.charAt b32 (bit-and (bit-shift-right val (- bits 5)) 31)))
        (recur val (- bits 5)))
      [val bits])))

(defn base32
  "Port of cid.py _base32: 5-bit grouping of a byte seq → base32-lower (no padding).
  `data` is a seq of ints in 0..255."
  [data]
  (let [sb (StringBuilder.)]
    (loop [data (seq data), val 0, bits 0]
      (if (empty? data)
        (do
          (when (pos? bits)
            (.append sb (.charAt b32 (bit-and (bit-shift-left val (- 5 bits)) 31))))
          (.toString sb))
        (let [b (bit-and (int (first data)) 0xff)
              [val bits] (drain sb (bit-or (bit-shift-left val 8) b) (+ bits 8))]
          (recur (rest data) val bits))))))

#?(:clj
   (defn sha256-bytes
     "sha2-256 of a byte-array → seq of unsigned ints (0..255). Host edge: java.security."
     [^bytes ba]
     (let [md (java.security.MessageDigest/getInstance "SHA-256")
           digest (.digest md ba)]
       (map #(bit-and (int %) 0xff) digest))))

#?(:clj
   (defn ->bytes
     "Coerce a String (UTF-8) or byte-array to a byte-array (the document body)."
     ^bytes [data]
     (if (string? data) (.getBytes ^String data "UTF-8") data)))

#?(:clj
   (defn cidv1-raw
     "CIDv1 / raw (0x55) / sha2-256 — matches `ipfs add --cid-version=1 --raw-leaves`.
     `data` is a String (UTF-8) or byte-array. Mirrors cidv1_raw():
       mh  = [0x12 0x20] ++ sha256(data)   ; sha2-256, 32-byte digest
       cid = [0x01 0x55] ++ mh             ; CIDv1, raw codec
       → \"b\" ++ base32(cid)"
     [data]
     (let [digest (sha256-bytes (->bytes data))
           mh (concat [0x12 0x20] digest)
           cid (concat [0x01 0x55] mh)]
       (str "b" (base32 cid)))))

#?(:clj
   (defn sha256-hex
     "0x-prefixed lowercase hex SHA-256 — the esign documentSha256 defense-in-depth hash.
     Mirrors sha256_hex(): \"0x\" + hashlib.sha256(data).hexdigest()."
     [data]
     (str "0x"
          (apply str (map #(format "%02x" %) (sha256-bytes (->bytes data)))))))

;; ipfs default chunk size; above this the raw CID no longer applies (dag-pb tree).
(def single-block-limit (* 256 1024))
