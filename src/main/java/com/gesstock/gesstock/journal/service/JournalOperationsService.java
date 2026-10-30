package com.gesstock.gesstock.journal.service;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.JournalActivite;
import com.gesstock.gesstock.journal.dto.JournalOperationsResponse;
import com.gesstock.gesstock.journal.dto.NombreParLibelle;
import com.gesstock.gesstock.journal.dto.OperationJournal;
import com.gesstock.gesstock.repositories.JournalActiviteRepository;
import com.gesstock.gesstock.ventes.service.HistoriqueVentesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le journal des opérations d'une période, pour l'écran et pour les documents imprimés : qui a fait quoi, quand, en clair. Pour
 * tous les utilisateurs ou pour un seul, toutes les opérations ou une famille (ventes, caisse, connexions et comptes…). Réservé aux
 * responsables (SecurityConfig).
 */
@Service
@Transactional(readOnly = true)
public class JournalOperationsService {

    public static final int OPERATIONS_ECRAN_MAX = 5_000;
    public static final int OPERATIONS_DOCUMENT_MAX = 50_000;
    /** Nom donné dans le résumé aux opérations sans compte (connexion refusée d'un identifiant inconnu). */
    static final String SANS_COMPTE = "Sans compte";

    private final JournalActiviteRepository journalRepository;

    public JournalOperationsService(JournalActiviteRepository journalRepository) {
        this.journalRepository = journalRepository;
    }

    /**
     * @param utilisateur le compte dont on veut les opérations, ou {@code null} pour tous
     * @param famille     une famille d'opérations, ou {@code null} pour toutes
     */
    public JournalOperationsResponse journal(LocalDate debut, LocalDate fin, String utilisateur, OperationsConnues.Famille famille) {
        return construire(debut, fin, charger(debut, fin, utilisateur, famille, OPERATIONS_ECRAN_MAX, "l'afficher"));
    }

    /** Les opérations de la période, les plus récentes d'abord. */
    public List<JournalActivite> charger(LocalDate debut, LocalDate fin, String utilisateur, OperationsConnues.Famille famille,
                                        int limite, String usage) {
        HistoriqueVentesService.verifierPeriode(debut, fin);
        var du = debut.atStartOfDay();
        var au = fin.plusDays(1).atStartOfDay();
        long total = journalRepository.compterOperationsDeLaPeriode(du, au, utilisateur);
        if (total > limite) {
            throw new RequeteInvalideException("Cette période compte " + total + " opérations : c'est trop pour " + usage
                + " (" + limite + " au plus). Choisissez une période plus courte ou un seul utilisateur.");
        }
        List<JournalActivite> operations = journalRepository.operationsDeLaPeriode(du, au, utilisateur);
        return famille == null ? operations
            : operations.stream().filter(operation -> OperationsConnues.famille(operation.getAction(), operation.getEntite()) == famille).toList();
    }

    public JournalOperationsResponse construire(LocalDate debut, LocalDate fin, List<JournalActivite> activites) {
        List<OperationJournal> operations = activites.stream().map(JournalOperationsService::versOperation).toList();
        Map<String, Long> parUtilisateur = new LinkedHashMap<>();
        Map<String, Long> parOperation = new LinkedHashMap<>();
        for (OperationJournal operation : operations) {
            parUtilisateur.merge(operation.utilisateur() == null ? SANS_COMPTE : operation.utilisateur(), 1L, Long::sum);
            parOperation.merge(operation.operation(), 1L, Long::sum);
        }
        return new JournalOperationsResponse(debut, fin, operations.size(), lesPlusNombreux(parUtilisateur), lesPlusNombreux(parOperation),
            operations);
    }

    private static List<NombreParLibelle> lesPlusNombreux(Map<String, Long> nombres) {
        return nombres.entrySet().stream()
            .map(entree -> new NombreParLibelle(entree.getKey(), entree.getValue()))
            .sorted(Comparator.comparingLong(NombreParLibelle::nombre).reversed()
                .thenComparing(NombreParLibelle::libelle, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static OperationJournal versOperation(JournalActivite activite) {
        return new OperationJournal(
            activite.getId(),
            activite.getDateActivite(),
            activite.getUtilisateur() == null ? null : activite.getUtilisateur().getUsername(),
            OperationsConnues.famille(activite.getAction(), activite.getEntite()).libelle(),
            OperationsConnues.libelle(activite.getAction(), activite.getEntite()),
            activite.getDetails(),
            activite.getEntite() + (activite.getEntiteId() == null ? "" : " #" + activite.getEntiteId()),
            activite.getAdresseIp());
    }
}
