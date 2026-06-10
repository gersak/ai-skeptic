#!/usr/bin/env bb
;; Append one "idiot" event (a bad Claude response + the user's reason) to
;; ~/.ai-skeptic/idiots.edn. Append-only; /bubble_cost re-derives day counts from it.
(ns idiot
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.pprint :as pp])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

(def home (str (fs/home)))
(def base (str home "/.ai-skeptic"))
(def file (str base "/idiots.edn"))

(defn current-session []
  (let [enc (str/replace (System/getProperty "user.dir") #"[^A-Za-z0-9]" "-")
        dir (str home "/.claude/projects/" enc)]
    (when (fs/exists? dir)
      (when-let [jsonls (seq (filter #(str/ends-with? (str %) ".jsonl") (fs/list-dir dir)))]
        (-> (apply max-key #(.toMillis (fs/last-modified-time %)) jsonls)
            fs/file-name str (str/replace #"\.jsonl$" ""))))))

(defn -main [args]
  (let [reason (str/trim (str/join " " args))]
    (when (empty? reason)
      (println "usage: idiot.clj <why it was idiotic>") (System/exit 1))
    (let [now  (LocalDateTime/now)
          date (.format now (DateTimeFormatter/ofPattern "yyyy-MM-dd"))
          time (.format now (DateTimeFormatter/ofPattern "HH:mm:ss"))
          existing (if (fs/exists? file)
                     (try (edn/read-string (slurp file)) (catch Exception _ []))
                     [])
          entry {:date date :time time :ts (str date "T" time)
                 :reason reason
                 :session (current-session)
                 :cwd (System/getProperty "user.dir")}
          updated (conj (vec existing) entry)
          n (count (filter #(= (:date %) date) updated))]
      (fs/create-dirs base)
      (spit file (with-out-str (pp/pprint updated)))
      (println (str "🤦 Logged idiot #" n " for " date ": " reason)))))

(-main *command-line-args*)
