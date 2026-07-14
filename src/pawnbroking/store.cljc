(ns pawnbroking.store
  "SSoT for the ISCO-08 4213 independent pawnbroking & small-loan
  practice actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md
  Actors section; README's 'Robotics premise' — a collateral intake,
  appraisal-support and vault-handling robot performs item
  photographing, tagging and secure vault storage under this
  advisor/governor pair, which never dispatches hardware itself and
  never disburses a loan above an item's registered appraised value).
  Modeled on cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered individual/customer (:client-id, :name)
    item   — a registered pledged item {:item-id :client-id :name
             :appraised-value number :appraisal-completed? boolean}.
             `:appraised-value` is the registered appraisal ceiling a
             proposed loan amount must not exceed — a loan beyond the
             item's registered appraised value is an unsecured
             advance, not a pawn loan. `:appraisal-completed?` records
             whether a formal appraisal has been completed for this
             item — offering a loan against unappraised collateral is
             an unsecured guess, not a pawn valuation.
    record — a committed operating record (an offered loan) — written
             ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (item [s item-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-item! [s i])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (item [_ item-id] (get-in @a [:items item-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-item! [s i]
    (swap! a assoc-in [:items (:item-id i)] i) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :items {} :records [] :ledger []}
                                   seed)))))
