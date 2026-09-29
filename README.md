# kadode 門出

**Labour-resignation concierge + 使者 (退職代行).** Helps a worker resign with dignity (門出 =
setting out on a new path), grounded in actual Japanese labour law, and — within a strict
non-lawyer boundary — relays the worker's already-formed unilateral resignation as a 使者
(messenger). The charter-clean, **free** inversion of a commercial 退職代行 業者.

- **ADR**: 2606112238 · **Status**: 🟡 R0
- **Schema**: `00-contracts/schemas/labor-exit-ontology.kotoba.edn`
- **Lexicons**: `com.etzhayyim.kadode.{resignationRelay,escalation}`

## What it does

- **Classifies** the worker's situation and recommends the **lawful route** on the escalation
  ladder — self → 使者 messenger → labour union → lawyer.
- **Generates** the worker's OWN resignation documents (退職届 / 退職願 / 即時退職通知 / 内容証明 /
  有給取得届), content-addressed, citing their statutory basis (民法627 / 628).
- **Relays** (as a 使者) a non-negotiating unilateral resignation — and **refuses + escalates**
  anything needing negotiation.

## The non-弁 (UPL) boundary — what makes it charter-clean (G1)

kadode is a **使者 (messenger), never a 代理人 (agent), and never the practice of law**. It relays
an already-formed unilateral resignation (民法627 — the employer's consent is not required) and
drafts the worker's own documents. It **does not negotiate** terms, severance, or settlements
(弁護士法72条). The moment a matter needs negotiation — unpaid wages, 損害賠償 threats,
harassment claims, disputed leave — kadode hands off to a **labour union** (団体交渉) or a
**lawyer**. This boundary is enforced in code and proven by tests, not just documented.

It surfaces the worker's rights against common 引き止め: 退職拒否 → 民法627 (unilateral, no consent
needed); 損害賠償脅迫 → 労基法16条 (賠償予定の禁止); 有給拒否 → 労基法39条; 離職票不交付 → 雇用保険法;
強制労働的拘束 → 労基法5条.

## Run

```bash
cd 20-actors/kadode
python3 methods/analyze.py && python3 methods/coverage_report.py
python3 methods/generate.py --kind taishoku-todoke --worker 山田太郎 --employer 株式会社ABC --date 令和8年7月15日
python3 tests/test_analyze.py && python3 tests/test_generate.py && python3 tests/test_coverage.py && python3 tests/test_wasm.py
```

See `AGENTS.md` for the full gate set.
