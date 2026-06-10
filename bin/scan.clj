#!/usr/bin/env bb
;; Rebuild ~/.ai-skeptic/{sessions,days}.edn from Claude Code transcripts, then
;; print a Usage-tab-style summary. Read-only against transcripts; idempotent.
(ns scan
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]))

(def home (str (fs/home)))
(def projects (str home "/.claude/projects"))
(def base (str home "/.ai-skeptic"))
(def idiots-file (str base "/idiots.edn"))

;; ---- pricing: $/1M tokens (input output); cache derived from input ----
(def price {"opus" [5.0 25.0] "sonnet" [3.0 15.0] "haiku" [1.0 5.0]})

(defn base-for [model]
  (let [m (str/lower-case (or model ""))]
    (cond (str/includes? m "haiku")  (price "haiku")
          (str/includes? m "sonnet") (price "sonnet")
          :else                      (price "opus"))))

(defn components [u]
  (let [cc (:cache_creation u)
        w5 (:ephemeral_5m_input_tokens cc)
        w1 (:ephemeral_1h_input_tokens cc)
        [w5 w1] (if (and (nil? w5) (nil? w1))
                  [(or (:cache_creation_input_tokens u) 0) 0]
                  [(or w5 0) (or w1 0)])]
    {:input (or (:input_tokens u) 0)
     :output (or (:output_tokens u) 0)
     :cache-read (or (:cache_read_input_tokens u) 0)
     :w5 w5 :w1 w1}))

(defn cost [model {:keys [input output cache-read w5 w1]}]
  (let [[ip op] (base-for model)]
    (/ (+ (* input ip) (* output op) (* cache-read ip 0.1)
          (* w5 ip 1.25) (* w1 ip 2.0))
       1000000.0)))

(defn round2 [x] (/ (Math/round (* (double x) 100.0)) 100.0))
(defn round4 [x] (/ (Math/round (* (double x) 10000.0)) 10000.0))

(defn earliest [a b] (if (neg? (compare a b)) a b))
(defn latest [a b] (if (pos? (compare a b)) a b))

(defn local-day [ts]
  (when ts
    (-> (java.time.Instant/parse ts)
        (.atZone (java.time.ZoneId/systemDefault))
        (.toLocalDate) str)))

(defn wall-ms [a b]
  (if (and a b)
    (.toMillis (java.time.Duration/between
                (java.time.Instant/parse a) (java.time.Instant/parse b)))
    0))

;; ---- scan one transcript into raw accumulators ----
(def empty-tok {:input 0 :output 0 :cache-read 0 :cache-write 0})

(defn scan-file [path]
  (with-open [r (io/reader path)]
    (reduce
     (fn [acc line]
       (let [rec (try (json/parse-string line true) (catch Exception _ nil))]
         (if (nil? rec)
           acc
           (let [ts (:timestamp rec)
                 acc (cond-> acc
                       ts (update :first #(if % (earliest % ts) ts))
                       ts (update :last  #(if % (latest % ts) ts))
                       (and (nil? (:cwd acc)) (:cwd rec)) (assoc :cwd (:cwd rec)))]
             (cond
               (and (= "system" (:type rec)) (= "turn_duration" (:subtype rec)))
               (update acc :api-ms + (or (:durationMs rec) 0))

               (and (= "assistant" (:type rec)) (get-in rec [:message :usage]))
               (let [msg (:message rec) mid (:id msg)]
                 (if (or (nil? mid) (contains? (:seen acc) mid))
                   acc
                   (let [comp (components (:usage msg))
                         model (or (:model msg) "claude-opus-4-8")
                         c (cost model comp)
                         tok {:input (:input comp) :output (:output comp)
                              :cache-read (:cache-read comp)
                              :cache-write (+ (:w5 comp) (:w1 comp)) :cost c}]
                     (-> acc
                         (update :seen conj mid)
                         (update-in [:per-model model]
                                    #(merge-with + (or % (assoc empty-tok :cost 0.0)) tok))))))

               (map? (:toolUseResult rec))
               (let [[a d] (reduce
                            (fn [acc2 hunk]
                              (reduce (fn [[a d] ln]
                                        (cond (str/starts-with? ln "+") [(inc a) d]
                                              (str/starts-with? ln "-") [a (inc d)]
                                              :else [a d]))
                                      acc2 (:lines hunk)))
                            [0 0] (:structuredPatch (:toolUseResult rec)))]
                 (-> acc (update :added + a) (update :removed + d)))

               :else acc)))))
     {:seen #{} :per-model {} :api-ms 0 :added 0 :removed 0 :first nil :last nil :cwd nil}
     (line-seq r))))

(defn session-record [path acc]
  (let [models (:per-model acc)
        by-model (->> models
                      (map (fn [[m t]] {:model m :cost (round4 (:cost t))
                                        :tokens (dissoc t :cost)}))
                      (sort-by (comp - :cost)) vec)
        total-cost (reduce + 0.0 (map :cost (vals models)))
        total-tok (reduce #(merge-with + %1 (dissoc %2 :cost)) empty-tok (vals models))]
    {:session (str/replace (str (fs/file-name path)) #"\.jsonl$" "")
     :project (:cwd acc)
     :day (local-day (:first acc))
     :started (:first acc) :ended (:last acc)
     :api-ms (:api-ms acc) :wall-ms (wall-ms (:first acc) (:last acc))
     :lines-added (:added acc) :lines-removed (:removed acc)
     :cost (round2 total-cost)
     :tokens total-tok
     :by-model by-model}))

(defn read-idiots []
  (if (fs/exists? idiots-file)
    (try (edn/read-string (slurp idiots-file)) (catch Exception _ []))
    []))

(defn build-days [sessions idiots]
  (let [icount (frequencies (map :date idiots))]
    (->> (group-by :day sessions)
         (map (fn [[day sess]]
                {:day day
                 :cost (round2 (reduce + 0.0 (map :cost sess)))
                 :sessions (count sess)
                 :idiots (get icount day 0)
                 :tokens (reduce #(merge-with + %1 (:tokens %2)) empty-tok sess)
                 :by-model (->> (mapcat :by-model sess)
                                (group-by :model)
                                (map (fn [[m xs]]
                                       {:model m
                                        :cost (round4 (reduce + 0.0 (map :cost xs)))
                                        :tokens (reduce #(merge-with + %1 (:tokens %2)) empty-tok xs)}))
                                (sort-by (comp - :cost)) vec)}))
         (sort-by :day) vec)))

;; ---- printing ----
(defn money [c] (if (and (> c 0) (< c 0.01)) (format "$%.4f" c) (format "$%.2f" c)))
(defn human [n]
  (cond (>= n 1000000) (format "%.1fM" (/ (double n) 1e6))
        (>= n 1000)    (format "%.1fk" (/ (double n) 1e3))
        :else          (str (long n))))
(defn fmt-dur [ms]
  (let [s (long (/ (or ms 0) 1000))
        d (quot s 86400) s (rem s 86400)
        h (quot s 3600)  s (rem s 3600)
        m (quot s 60)    s (rem s 60)]
    (cond (pos? d) (format "%dd %dh %dm" d h m)
          (pos? h) (format "%dh %dm" h m)
          :else    (format "%dm %ds" m s))))

(def dash (str "  " (apply str (repeat 12 "-")) " " (apply str (repeat 9 "-")) "   ---"))

(defn print-report [sessions days]
  (let [cwd (System/getProperty "user.dir")
        cur (->> sessions (filter #(= (:project %) cwd)) (sort-by :started) last)]
    (when cur
      (println (str "  SESSION  (" (subs (:session cur) 0 8) ")\n"))
      (println (str "  Total cost:        " (money (:cost cur))))
      (println (str "  Duration (API):    " (fmt-dur (:api-ms cur))))
      (println (str "  Duration (wall):   " (fmt-dur (:wall-ms cur))))
      (println (str "  Code changes:      " (:lines-added cur) " lines added, " (:lines-removed cur) " removed"))
      (println "  Usage by model:")
      (doseq [{:keys [model cost tokens]} (:by-model cur)]
        (println (format "    %-20s %s input · %s output · %s cache read · %s cache write   (%s)"
                         (str model ":") (human (:input tokens)) (human (:output tokens))
                         (human (:cache-read tokens)) (human (:cache-write tokens)) (money cost)))))
    (println "\n  PER DAY  (all projects)\n")
    (println (format "  %-12s %9s   %s" "day" "cost" "🤦"))
    (println dash)
    (doseq [{:keys [day cost idiots]} (sort-by :day #(compare %2 %1) days)]
      (println (format "  %-12s %9s   %3d" day (money cost) idiots)))
    (println dash)
    (println (format "  %-12s %9s   %3d" "TOTAL"
                     (money (reduce + 0.0 (map :cost days)))
                     (reduce + 0 (map :idiots days))))
    (println (str "\n  Estimated at list token prices; actual billing may differ."))
    (println (str "  EDN → " base "/{sessions,days,idiots}.edn   ·   /idiot <reason> to log"))))

;; ---- main ----
(defn -main []
  (when-not (fs/exists? projects)
    (println "no Claude Code transcripts found at" projects) (System/exit 0))
  (let [files (map str (fs/glob projects "*/*.jsonl"))
        sessions (->> files
                      (map (fn [p] (session-record p (scan-file p))))
                      (filter :day)
                      (sort-by :started) vec)
        idiots (read-idiots)
        days (build-days sessions idiots)]
    (fs/create-dirs base)
    (spit (str base "/sessions.edn") (with-out-str (pp/pprint sessions)))
    (spit (str base "/days.edn") (with-out-str (pp/pprint days)))
    (print-report sessions days)))

(-main)
