(ns kadode.methods.datom-emit
  "kadode 門出 — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).
  1:1 Clojure port of `methods/datom_emit.py` (ADR-2606112238).

  Projects the labour-exit graph into append-only kotoba Datoms [e a v tx op] — first-class
  canonical state. GROUND (durable, :add) = the scenario / ground / document / route / risk
  nodes and their :en/* 縁 (incl. every :requires-route + :upl-bound edge encoding the
  弁護士法72条 boundary). DERIVED (transient, :bond/is-transient) = the edge-primary
  ground-support / risk-coverage integrals, computed on read (N1/G2), never persisted.

  Reuses kadode.methods.analyze (read-edn / load-graph / analyze). House style: Python ':…'
  keyword strings stay strings; the emitted Datom text is byte-identical to the Python emit.
  Float formatting mirrors Python's `{v:g}`.

  NODE ORDERING (byte-parity): the Python `for nid in nodes` walks the dict in EDN read order.
  kadode.methods.analyze/load-graph returns a hash-map (order lost beyond 8 keys), so this ns
  re-derives the first-touch node-id order from the parsed forms and threads it via ::node-order
  metadata on the nodes map (load-file*), falling back to (keys nodes)."
  (:require [clojure.string :as str]
            [kadode.methods.analyze :as analyze]
            #?(:clj [clojure.java.io :as io])))

;; attributes promoted from each node/edge map into ground datoms (stable order = determinism)
(def node-attrs
  [":lx/kind" ":lx/label" ":lx/sourcing" ":lx/links"
   ":scenario/employment" ":scenario/needs-negotiation"
   ":ground/citation" ":ground/instrument" ":ground/url"
   ":document/kind" ":document/binding"
   ":route/actor" ":route/can-negotiate" ":risk/pattern"])

(def edge-attrs
  [":en/from" ":en/to" ":en/kind" ":en/weight" ":en/force" ":en/sourcing"])

(defn- fmt-g
  "Mirror Python's f-string `{v:g}` for our (moderate-magnitude) doubles: 6 significant
  digits, trailing zeros stripped, an integral value renders with no decimal point."
  [v]
  (let [d (double v)]
    (if (and (not (Double/isInfinite d)) (not (Double/isNaN d))
             (== d (Math/rint d)) (< (Math/abs d) 1e15))
      (str (long d))
      (let [s (format "%.6g" d)]
        (if (str/includes? s ".")
          (-> s (str/replace #"0+$" "") (str/replace #"\.$" ""))
          s)))))

(defn fmt
  "Port of _fmt: bool → true/false; nil → nil; \":…\" kept literal; other string → quoted
  with \\ and \" escaped; float (double) → {v:g}; else str()."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (string? v) (if (str/starts-with? v ":")
                  v
                  (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    (double? v) (fmt-g v)
    :else (str v)))

(defn- node-order
  "First-touch node-id order: ::node-order metadata if present (load-file*), else (keys nodes)."
  [nodes]
  (or (::node-order (meta nodes)) (keys nodes)))

(defn- lstrip-colon
  "Python str.lstrip(':') — strips ALL leading colons (here at most one)."
  [s]
  (if (str/starts-with? s ":") (subs s 1) s))

(defn- ranked-items
  "res[k].items() sorted by (-value, key) — mirrors Python's
  `sorted(res[k].items(), key=lambda kv: (-kv[1], kv[0]))`."
  [d]
  (sort-by (fn [nid] [(- (double (get d nid))) nid]) (keys d)))

(defn emit
  "Faithful 1:1 of datom_emit.emit. Returns the kotoba Datom-log EDN text (trailing newline)."
  ([nodes edges res] (emit nodes edges res 1))
  ([nodes edges res tx]
   (let [L (transient [])]
     (conj! L ";; kadode 門出 — GENERATED kotoba Datom log (ADR-2606112238). DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; GROUND op :add = durable. DERIVED :bond/is-transient = computed on read (N1/G2).")
     (conj! L ";; G1: kadode is a 使者 (messenger), never an agent; routes encode the 弁護士法72条 boundary.")
     (conj! L "[")

     ;; ── GROUND: node datoms (insertion / EDN read order → deterministic)
     (doseq [nid (node-order nodes)]
       (let [nd (get nodes nid)]
         (doseq [a node-attrs]
           (when (and (contains? nd a) (not (nil? (get nd a))))
             (conj! L (str "[" (fmt nid) " " a " " (fmt (get nd a)) " " tx " :add]"))))))

     ;; ── GROUND: edge datoms (edge entity id is content-stable: en.<from>.<kind>.<to>)
     (doseq [e edges]
       (let [eid (str "en." (get e ":en/from") "."
                      (lstrip-colon (get e ":en/kind"))
                      "." (get e ":en/to"))]
         (doseq [a edge-attrs]
           (when (and (contains? e a) (not (nil? (get e a))))
             (conj! L (str "[" (fmt eid) " " a " " (fmt (get e a)) " " tx " :add]"))))))

     ;; ── DERIVED (transient — NOT persisted; N1/G2)
     (conj! L ";; ── DERIVED readouts (transient; integral of incident 縁, computed on read) ──")
     (doseq [nid (ranked-items (get res "ground_support"))]
       (conj! L (str "[" (fmt nid) " :bond/ground-support " (fmt-g (get-in res ["ground_support" nid]))
                     " " tx " :derived] ;; :bond/is-transient true")))
     (doseq [nid (ranked-items (get res "risk_coverage"))]
       (conj! L (str "[" (fmt nid) " :bond/risk-coverage " (fmt-g (get-in res ["risk_coverage" nid]))
                     " " tx " :derived] ;; :bond/is-transient true")))

     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

(defn load-graph*
  "Like analyze/load-graph but attaches ::node-order (first-touch node-id order from the
  parsed forms) to the returned :nodes map, so emit walks nodes in EDN read order."
  [forms]
  (let [{:keys [nodes edges]} (analyze/load-graph forms)
        order (->> forms
                   (filter map?)
                   (filter #(contains? % ":lx/id"))
                   (mapv #(get % ":lx/id"))
                   (distinct)
                   (vec))]
    {:nodes (with-meta nodes {::node-order order}) :edges edges}))

#?(:clj
   (defn load-file*
     "Read + parse a labour-exit EDN graph file → {:nodes :edges} with ::node-order metadata."
     [path]
     (load-graph* (analyze/read-edn (slurp (str path))))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/resignation-datoms.kotoba.edn (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-resignation-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           tx (if (some #{"--tx"} argv)
                (Long/parseLong (nth argv (inc (.indexOf argv "--tx"))))
                1)
           {:keys [nodes edges]} (load-file* seed)
           res (analyze/analyze nodes edges)
           out (io/file outdir "resignation-datoms.kotoba.edn")]
       (.mkdirs outdir)
       (spit out (emit nodes edges res tx))
       (println (str "kadode datom log → " out " (" (count nodes) " nodes + " (count edges)
                     " 縁, tx=" tx ")"))
       0)))
