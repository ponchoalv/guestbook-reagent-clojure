(ns guestbook.ws 
  (:require 
   [taoensso.sente :as sente]))

(enable-console-print!)

(let [connection (sente/make-channel-socket! "/ws" {:type :auto
                                                    :packer :edn})]
  (def ch-chsk (:ch-recv connection))
  (def send-message! (:send-fn connection))
  (def state (:state connection)))

(defmulti -event-msg-handler :id)

(defmethod -event-msg-handler :default [{:as ev-msg :keys [event]} _]
  (println "Unhandled event: " ev-msg))

(defmethod -event-msg-handler :chsk/handshake [{:keys [id ?data event]} _]
  (println "Connection established: id -> " id " event -> " event " data -> " ?data))

(defmethod -event-msg-handler :chsk/state [{:keys [?data]} _]
  (println "State changed: " ?data))


;; para el evento :chsk/recv se toma el segundo parametro y
;; se usa para llamar un metodo sin necesidad de acoplar core con ws.
(defmethod -event-msg-handler :chsk/recv [ev-msg recv-handler]
  (recv-handler ev-msg))

;; Wraper, se puede utilizar no solo para tomar parametros
;; tambi�n pueden ponerse loggers y otros middlewares.
(defn event-msg-handler [& [recv-handler]]
  (fn [ev-msg]
    (-event-msg-handler ev-msg recv-handler)))

(defonce router (atom nil))

(defn stop-router! []
  (when-let [stop-f @router] (stop-f)))

(defn start-router! [message-handler]
  (stop-router!)
  (reset! router (sente/start-chsk-router!
                  ch-chsk
                  (event-msg-handler message-handler))))
