(ns nansi.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [clojure.string :as string]
   [clojure.tools.cli :refer [parse-opts]]
   [cljs.core.async :refer [put! chan <! >! timeout close!]]
   [nansi.mirror :as m]
   [cljs.nodejs :as nodejs]))

(nodejs/enable-util-print!)
(def fs (nodejs/require "fs"))

(defn usage [options-summary]
  (->> ["This is my program. There are many like it, but this one is mine."
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  start    Start a new server"
        "  stop     Stop an existing server"
        "  status   Print a server's status"
        ""
        "Please refer to the manual page for more information."]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (.exit js/process status))

(defn valid-file?
  [file]
  (let [ch (chan)]
      (.access fs
                 file
                 (aget fs "constants" "R_OK")
                 (fn [err]
                   (if err
                     (go
                       (>! ch false)
                       (>! ch true)))))
        ))

(def cli-options
  ;; An option with a required argument
  [["-f" "--file FILE" "Filename"
    ;; :default 80
    ;; :parse-fn #(Integer/parseInt %)
    ;; :validate [#(< 0 % 0x10000) "Must be a valid file"]
    :validate [#(let [ch (chan)]
                  (.access fs
                            %
                            (aget fs "constants" "R_OK")
                            (fn [err]
                              (if err
                                (reset! error true)
                                (reset! error false))))
                  @error)
               "Must be a valid file"]]
   ;; A non-idempotent option
   ["-v" nil "Verbosity level"
    :id :verbosity
    :default 0
    :assoc-fn (fn [m k _] (update-in m [k] inc))]
   ;; A boolean option defaulting to nil
   ["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))

    ;; Execute program with options
    (case (first arguments)
      "test" (valid-file? "resources/partial.txt")
      "read-file" (.access fs
                           "resources/partial.txt"
                            (aget fs "constants" "R_OK")
                            (fn [err]
                              (if err
                                (println "error")
                                (println "no error"))))
      ;; "mirror" (mirror/output)
      "mirror" (println "stop called")
      "options" (println options)
      (exit 1 (usage summary)))))

(set! *main-cli-fn* -main)
