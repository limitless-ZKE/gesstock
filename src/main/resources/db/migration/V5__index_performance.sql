-- Index absents du schéma initial : les rapports et l'historique filtrent par statut et par date.
CREATE INDEX idx_vente_statut_date        ON vente (statut, date_vente);
CREATE INDEX idx_vente_session_statut     ON vente (session_caisse_id, statut);
CREATE INDEX idx_journal_date             ON journal_activites (date_activite);
CREATE INDEX idx_mvt_stock_article_date   ON mouvements_stock (article_id, date_mouvement);
CREATE INDEX idx_dettes_statut_client     ON dettes_clients (statut, client_id);
CREATE INDEX idx_reglements_session_mode  ON reglements_clients (session_caisse_id, mode_reglement);
