(ns guestbook.routes.ws
  (:require [compojure.core :refer [defroutes GET POST]] 
            [guestbook.db.core :as db]
            [bouncer.core :as b]
            [bouncer.validators :as v]
            [mount.core :refer [defstate]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant :refer (get-sch-adapter)]))


;; Logica de validación utilizada necesaria segun el evento ejecutado
(defn validate-message [params]
  (first
   (b/validate
    params
    :name [[v/required :message "El nombre es obligatorio."]]
    :message [[v/required :message "El mensaje es obligatorio."] 
              [v/min-count 10
               :message
               "El mensaje debe ser al menos de 10 caracteres de largo."]])))

(defn save-message! [message]
  (if-let [errors (validate-message message)]
    {:errors errors}
    (do
      (db/save-message! message)
      message)))


;; Logica de la conexión por WS
(let [connection (sente/make-channel-socket!
                  (get-sch-adapter)
                  {:user-id-fn
                   (fn [ring-req]
                     (get-in ring-req [:params :client-id]))})]
  
  (def ring-ajax-post (:ajax-post-fn connection))
  (def ring-ajax-get-or-ws-handshake (:ajax-get-or-ws-handshake-fn connection))
  (def ch-chsk (:ch-recv connection))
  (def chsk-send! (:send-fn connection))
  (def connected-uids (:connected-uids connection)))


(defmulti event :id)

(defmethod event :default [{:as ev-msg :keys [event]}]
  (println "Unhandled event: " event))

(defmethod event :chsk/uidport-open [{:keys [uid client-id]}]
  (println "New connection:" uid client-id))

(defmethod event :chsk/uidport-close [{:keys [uid]}]
  (println "Disconnected:" uid))

(defmethod event :chsk/ws-ping [_])

(defmethod event :guestbook/add-message [{:keys [client-id ?data]}]
  (println "New :guestbook/add-message event dispatched whit data: " ?data)
  (let [response (-> ?data 
                     (assoc :timestamp (java.util.Date.))
                     save-message!)]
    (if (:errors response)
      (chsk-send! client-id [:guestbook/error response])
      (doseq [uid (:any @connected-uids)]
        (chsk-send! uid
                    [:guestbook/add-message
                     (assoc response :uid client-id)])))))

(defn stop-router! [stop-fn]
  (when stop-fn (stop-fn)))

(defn start-router! []
  (println "\n\n+++++++++ STARTING ROUTER! ++++++++\n\n")
  (sente/start-chsk-router! ch-chsk event))

(defstate router
  :start (start-router!)
  :stop (stop-router! router))

(defroutes websocket-routes
  (GET "/ws" req (ring-ajax-get-or-ws-handshake req))
  (POST "/ws" req (ring-ajax-post req)))

