(ns pawnbroking.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [pawnbroking.store :as store]
            [pawnbroking.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Pawn"})
    (store/register-item! st {:item-id "I-1" :client-id "client-1"
                              :name "item-042"
                              :appraised-value 1000
                              :appraisal-completed? true})
    st))

(defn- offer-op [amount]
  {:op :approve-loan-offer :effect :propose :item-id "I-1"
   :loan-amount amount :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-appraisal-and-completed
  (let [st (fresh-store)
        v (governor/check req {} (offer-op 500) st)]
    (is (:ok? v))))

(deftest ok-at-exact-appraisal-boundary
  (testing "the appraised-value ceiling is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (offer-op 1000) st)]
      (is (:ok? v)))))

(deftest hard-on-loan-exceeds-appraised-value
  (testing "a loan beyond the item's registered appraised value is an unsecured advance, not a pawn loan"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (offer-op 5000) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :loan-exceeds-appraised-value (:rule %)) (:violations v))))))

(deftest hard-on-appraisal-not-completed
  (testing "offering a loan against unappraised collateral is an unsecured guess, not a pawn valuation"
    (let [st (store/mem-store)]
      (store/register-client! st {:client-id "client-1" :name "Kobo Pawn"})
      (store/register-item! st {:item-id "I-1" :client-id "client-1"
                                :name "item-042"
                                :appraised-value 1000
                                :appraisal-completed? false})
      (let [v (governor/check req {} (assoc (offer-op 500) :confidence 0.99) st)]
        (is (:hard? v))
        (is (some #(= :appraisal-not-completed (:rule %)) (:violations v)))))))

(deftest hard-on-unknown-item
  (let [st (fresh-store)
        v (governor/check req {} (assoc (offer-op 500) :item-id "I-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-item (:rule %)) (:violations v)))))

(deftest hard-on-foreign-item
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (offer-op 500) st)]
      (is (:hard? v))
      (is (some #(= :item-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (offer-op 500) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (offer-op 500) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-over-appraisal-disbursement-even-at-high-confidence
  (testing "no loan disbursement above the item's registered appraised-value ceiling without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-over-appraisal-disbursement :effect :propose
                                    :item-id "I-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-unappraised-loan-offer-even-at-high-confidence
  (testing "a loan cannot be offered until the collateral is registered and appraised, with human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-unappraised-loan-offer :effect :propose
                                    :item-id "I-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (offer-op 500) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
