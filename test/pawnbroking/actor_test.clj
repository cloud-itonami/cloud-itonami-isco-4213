(ns pawnbroking.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [pawnbroking.actor :as actor]
            [pawnbroking.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Pawn"})
    (store/register-item! st {:item-id "I-1" :client-id "client-1"
                              :name "item-042"
                              :appraised-value 1000
                              :appraisal-completed? true})
    st))

(deftest commits-a-within-appraisal-completed-offer
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-loan-offer :stake :low
                 :item-id "I-1" :loan-amount 500}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-appraisal-offer
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-loan-offer :stake :low
                 :item-id "I-1" :loan-amount 5000}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-over-appraisal-disbursement-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-over-appraisal-disbursement :stake :low
                 :item-id "I-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
