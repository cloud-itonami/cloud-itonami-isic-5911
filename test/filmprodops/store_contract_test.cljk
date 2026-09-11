(ns filmprodops.store-contract-test
  "Contract tests for `filmprodops.store/Store` protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [filmprodops.store :as store]))

(deftest mem-store-production-lookup
  (testing "MemStore can store and retrieve productions by ID (string keys)"
    (let [productions {"p1" {:production-id "p1" :title "Alpha" :registered? true :verified? true}}
          s (store/mem-store productions)]
      (is (some? (store/production s "p1")))
      (is (nil? (store/production s "p99"))))))

(deftest mem-store-all-productions
  (testing "MemStore returns all productions in sorted order"
    (let [productions {"p2" {:production-id "p2" :title "Bravo"}
                       "p1" {:production-id "p1" :title "Alpha"}
                       "p3" {:production-id "p3" :title "Charlie"}}
          s (store/mem-store productions)
          all-p (store/all-productions s)]
      (is (= 3 (count all-p)))
      (is (= "p1" (:production-id (first all-p))))
      (is (= "p3" (:production-id (last all-p)))))))

(deftest mem-store-ledger-append
  (testing "MemStore append-ledger! adds facts to immutable log"
    (let [s (store/mem-store {})
          fact1 {:t :test :data "fact1"}
          fact2 {:t :test :data "fact2"}]
      (is (= 0 (count (store/ledger s))))
      (store/append-ledger! s fact1)
      (is (= 1 (count (store/ledger s))))
      (store/append-ledger! s fact2)
      (is (= 2 (count (store/ledger s)))))))

(deftest mem-store-coordination-log
  (testing "MemStore commit-record! appends to coordination-log"
    (let [s (store/mem-store {})
          record {:op :log-production-record :production-id "p1" :value {:scene "1A"}}]
      (is (= 0 (count (store/coordination-log s))))
      (store/commit-record! s record)
      (is (= 1 (count (store/coordination-log s))))
      (is (= record (first (store/coordination-log s)))))))

(deftest mem-store-with-productions
  (testing "MemStore with-productions replaces the production directory"
    (let [s (store/mem-store {})
          new-productions {"p1" {:production-id "p1" :title "Alpha"}}]
      (is (= 0 (count (store/all-productions s))))
      (store/with-productions s new-productions)
      (is (= 1 (count (store/all-productions s)))))))

(deftest seed-db-has-demo-data
  (testing "seed-db creates a populated MemStore with demo productions"
    (let [s (store/seed-db)]
      (is (> (count (store/all-productions s)) 0))
      (is (some? (store/production s "production-1")))
      (is (some? (store/production s "production-2")))
      (is (some? (store/production s "production-3"))))))

(deftest demo-data-string-key-consistency
  (testing "demo-data uses string keys, not keywords, for production-id"
    (let [demo (store/demo-data)
          productions (:productions demo)]
      (doseq [[k v] productions]
        (is (string? k) "keys must be strings")
        (is (string? (:production-id v)) "production-id must be string")
        (is (= k (:production-id v)) "key must match production-id")))))

(deftest store-is-append-only
  (testing "appended facts are immutable and never removed"
    (let [s (store/seed-db)
          fact1 {:t :event1 :data "a"}
          fact2 {:t :event2 :data "b"}]
      (store/append-ledger! s fact1)
      (let [ledger-after-1 (store/ledger s)]
        (store/append-ledger! s fact2)
        (let [ledger-after-2 (store/ledger s)]
          (is (= (count ledger-after-1) (dec (count ledger-after-2))))
          (is (every? #(some (fn [x] (= x %)) ledger-after-2) ledger-after-1)
              "all prior facts must still be present"))))))
