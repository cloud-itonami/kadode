# kadode 門出 — labour-resignation concierge + 使者 (退職代行)

**ADR**: 2606112238 · **depends**: 2605262700 (chigiri 契 / legal-procedure substrate) +
2606111954 (hinagata 雛形 / legal-template commons, the document layer) · 2605181100 (himotoki
PII envelope) · 2605231525 (no-server-key) · 2605312345 (Datom = canonical state) · 2605215000
(Murakumo-only) · 2605261000 (Labor-Liberation ladder) · 2605302357 (Social Security §1.16).
**Status**: 🟡 R0 — resignation concierge (analyze + generate + UNSENT relay).

kadode ("門出" = setting out on a new path) is the **labour-liberation worker-EXIT concierge**:
it helps a worker resign with dignity, grounded in actual Japanese labour law, and — within a
strict non-lawyer boundary — **relays the worker's already-formed unilateral resignation as a
使者 (messenger)**. It is the worker-side sibling of the legal-concierge lineage (**toritsugi 取次**
procedure / **kurashimori 暮らし守** consumer / **tasuke 助** victim-support), reusing their
UPL-bounded, default-self-submit pattern, and it cross-links **hinagata 雛形** for the document
layer. It serves Charter §mission (労働の構造的解放) and Wellbecoming — a worker trapped in a
退職拒否 / ブラック企業 situation, set free with dignity.

This is the etzhayyim, charter-clean inversion of a commercial 退職代行 業者: **free**, worker
-authored, and honest about the **非弁 (UPL) boundary** that most of the industry blurs.

## Hard gates (constitutional — read before any change)

- **G1 — 使者 not 代理人 (messenger, not agent). THE defining boundary.** kadode RELAYS a
  worker's **already-formed, unilateral** resignation (民法627 is a unilateral right — the
  employer's consent is NOT required) and **drafts the worker's own documents**. It does NOT,
  and structurally CANNOT, **negotiate** terms, conditions, severance, or settlements — that is
  法律事務 reserved to lawyers (弁護士法72条, 非弁行為禁止) or to a labour union exercising
  団体交渉権 (労働組合法6). Any scenario that needs negotiation is **routed out** to a union or a
  lawyer. Enforced in code: `recommend_route()` raises on a graph that would route a
  negotiation-needing scenario to a 使者/self lane; `build_relay()` refuses such scenarios and
  returns the escalation. Test-covered (`test_g1_upl_invariant_holds_for_every_scenario`,
  `test_relay_refuses_negotiation_scenario_and_escalates`).
- **G2 — worker-authored + worker-decided.** The resignation is the **worker's own legal act**
  (their 民法627 right). kadode drafts + relays + informs; it never decides for the worker and
  never signs for them. Missing fields render as explicit blanks (`［　　］`), never invented.
- **G3 — non-adjudicating (N3).** Statute citations, grounds, risk-counters are **DISCLOSED
  legal facts** sourced from the instrument, never kadode verdicts. kadode never promises an
  outcome and never certifies a resignation's enforceability.
- **G4 — free.** No fee, no paid counsel (like tasuke). The worker submits their own documents.
- **G5 — sourcing honesty.** Every ground carries a public official URL (e-Gov / 厚労省).
  Coverage of all employment situations is bounded by design; the UPL invariant is **measured**
  by `coverage_report.py`, not assumed.
- **G6 — Murakumo-only narration** (ADR-2605215000).
- **G7 — outward-gated.** Actually **sending** the resignation (email / 内容証明 / 郵送) requires
  the worker's consent + an operator/Council step. R0 = analyze + generate + **UNSENT** relay.
- **G8 — no-server-key** (ADR-2605231525) **+ PII discipline.** kadode holds no key and sends
  nothing here; worker/employer PII rides a himotoki XChaCha20-Poly1305 envelope (ADR-2605181100),
  never plaintext on MST.

## The escalation ladder (the UPL boundary, encoded)

```
self  ──────────  worker self-submits 退職届 (default; 円満退職, 期間の定めなし)
messenger ──────  kadode 使者 relays the worker's unilateral resignation (no negotiation)
                  ── used when the worker can't face the employer / 退職拒否 / 即時退職
union ──────────  labour union 団体交渉 (CAN negotiate) — 未払い賃金 / 有給拒否 / 退職条件
lawyer ─────────  弁護士 (CAN negotiate + litigate) — 損害賠償脅迫 / ハラスメント責任追及 / 請求
```

Only **union** and **lawyer** carry `:route/can-negotiate true`. The analyzer constrains any
`:scenario/needs-negotiation true` situation to one of those two — never to self/messenger.

## Layout

```
20-actors/kadode/
├── AGENTS.md · README.md · manifest.jsonld
├── data/seed-resignation-graph.kotoba.edn   # scenario↔ground↔route↔risk graph (real JP labour law)
├── methods/                                  # pure-stdlib (no numpy) → kotoba pywasm-runnable
│   ├── analyze.py            # edge-primary UPL-bounded route recommendation
│   ├── datom_emit.py         # kotoba Datom-log (EAVT) emitter — canonical state
│   ├── generate.py           # 退職届/願/即時/内容証明/有給 renderer + 使者 relay (UPL-guarded)
│   ├── coverage_report.py    # honest coverage + UPL-invariant integrity check (G5)
│   └── cid.py                # kotoba IPFS CIDv1 (raw/sha2-256) — ipfs-parity, no daemon
├── tests/                    # 26 tests, pure stdlib (network-free)
│   └── test_analyze.py · test_generate.py · test_coverage.py · test_wasm.py
└── wasm/                     # kotoba pywasm component (componentize-py)
    ├── wit/world.wit · app.py · build.sh   # exports: analyze/datoms/coverage/generate/relay
```

## Run

```bash
cd 20-actors/kadode
python3 methods/analyze.py           # → out/route-report.md (route per scenario)
python3 methods/datom_emit.py        # → out/resignation-datoms.kotoba.edn (EAVT)
python3 methods/coverage_report.py   # → out/coverage-report.md (incl. UPL-invariant check)

# generate the worker's OWN resignation document (一身上の都合; never a demand — G1)
python3 methods/generate.py --kind taishoku-todoke --worker 山田太郎 --employer 株式会社ABC --date 令和8年7月15日

python3 tests/test_analyze.py && python3 tests/test_generate.py && python3 tests/test_coverage.py && python3 tests/test_wasm.py  # 26 green
```

## Ontology (labor-exit-ontology, `00-contracts/schemas/`)

- **nodes** `:lx/kind` ∈ `{:scenario, :ground, :document, :route, :risk}` with scenario
  (`:scenario/employment :scenario/needs-negotiation`), ground (`:ground/citation
  :ground/instrument :ground/url`), document (`:document/kind :document/binding`), route
  (`:route/actor :route/can-negotiate`) and risk (`:risk/pattern`) facets.
- **edges** `:en/kind` ∈ `{:supported-by, :requires-route, :produces, :rests-on, :counters,
  :triggers, :upl-bound}` carrying `:en/weight` ∈ [0,1] and a disclosed `:en/force`.
- **derived** `:bond/route-fit` · `:bond/ground-support` · `:bond/risk-coverage` — transient,
  computed on read (N1/G2).
- **route/negotiation-capability** the load-bearing table: only `:labor-union` + `:lawyer` are
  `true`; `:worker-self` + `:kadode-messenger` are `false` (G1 / 弁護士法72条).

## Cross-links

`:lx/links` bridges to siblings — **kokoro 心** (mental-health support when the exit is driven
by ハラスメント / 精神的負荷), **tasuke 助** (when there is a victimisation to pursue),
**hinagata 雛形** (the document/template layer), **chigiri 契** (general procedure), **himotoki**
(PII envelope). kadode conveys a worker's own resignation and informs them of their rights; it
does not advise, represent, negotiate, or certify.
