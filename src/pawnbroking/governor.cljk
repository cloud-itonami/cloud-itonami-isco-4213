(ns pawnbroking.governor
  "PawnbrokingGovernor — the independent safety/traceability layer
  named in this repository's README/business-model.md, gating every
  loan an advisor may offer against a pledged item. The governor
  never dispatches hardware itself and never disburses a loan above
  an item's registered appraised value. Modeled on
  cloud-itonami-isco-4311's bookkeeping.governor. Task twist: a
  proposed loan amount is an arithmetic ceiling against the item's
  registered appraised value, and a loan cannot be offered until the
  item's appraisal has been completed.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance      — the individual/customer must be
                                registered.
    2. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never disburses a loan above an item's
                                registered appraised value; it only
                                gates what the advisor may offer).
    3. item basis             — a loan-offer proposal must cite a
                                REGISTERED item belonging to this
                                client.
    4. appraised-value ceiling — the proposed loan amount must not
                                exceed the item's registered
                                `:appraised-value` (a loan beyond the
                                item's registered appraised value is
                                an unsecured advance, not a pawn
                                loan).
    5. appraisal completed    — the item must have
                                `:appraisal-completed?` true before any
                                loan can be offered (offering a loan
                                against unappraised collateral is an
                                unsecured guess, not a pawn
                                valuation).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    6. :op :approve-over-appraisal-disbursement (no loan disbursement
                                above the item's registered appraised-
                                value ceiling without the governor
                                gate).
    7. :op :approve-unappraised-loan-offer (a loan cannot be offered
                                until the collateral is registered and
                                appraised, with human sign-off).
    8. low confidence (< `confidence-floor`)."
  (:require [pawnbroking.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:approve-over-appraisal-disbursement
                                     :approve-unappraised-loan-offer})

(defn- hard-violations [{:keys [request proposal]} client-record i]
  (let [{:keys [op loan-amount]} proposal
        offer? (= :approve-loan-offer op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は登録鑑定額超過の貸付を直接実行しない）"})

      (and offer? (nil? i))
      (conj {:rule :unknown-item :detail "未登録 item への貸付提案は不可"})

      (and offer? i (not= (:client-id i) (:client-id request)))
      (conj {:rule :item-wrong-client :detail "item が別 client のもの"})

      (and offer? i (number? loan-amount) (> loan-amount (:appraised-value i)))
      (conj {:rule :loan-exceeds-appraised-value
             :detail (str "貸付額 " loan-amount " > 登録済み鑑定額 "
                          (:appraised-value i) "（登録済み鑑定額を超える貸付は無担保前貸であって質貸ではない）")})

      (and offer? i (not (:appraisal-completed? i)))
      (conj {:rule :appraisal-not-completed
             :detail "鑑定未完了の担保への貸付提案は無担保の当て推量であって質鑑定ではない"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `pawnbroking.store/Store`. Pure — never mutates
  the store, never disburses a loan above an item's registered
  appraised value."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        i (some->> (:item-id proposal) (store/item store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record i)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
