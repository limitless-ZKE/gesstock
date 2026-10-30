package com.gesstock.gesstock.ventes.service;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.DetteClient;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.ReglementClient;
import com.gesstock.gesstock.entities.StatutDette;
import com.gesstock.gesstock.repositories.ReglementClientRepository;
import com.gesstock.gesstock.ventes.dto.LigneReglementDette;
import com.gesstock.gesstock.ventes.dto.ReglementsDettesResponse;
import com.gesstock.gesstock.ventes.dto.ResumeReglementsDettes;
import com.gesstock.gesstock.ventes.dto.TotalParMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Historique des règlements de dettes d'une période, pour l'écran et les documents imprimés : chaque paiement reçu sur la dette
 * d'un client, qui l'a encaissé, et ce qui restait dû juste après. « Par vendeur » veut dire ici le compte qui a encaissé l'argent
 * (décision de l'utilisateur), comme dans le journal de caisse par caissier.
 */
@Service
@Transactional(readOnly = true)
public class HistoriqueReglementsService {

    public static final int REGLEMENTS_ECRAN_MAX = 5_000;
    public static final int REGLEMENTS_DOCUMENT_MAX = 50_000;

    private final ReglementClientRepository reglementRepository;

    public HistoriqueReglementsService(ReglementClientRepository reglementRepository) {
        this.reglementRepository = reglementRepository;
    }

    /** @param recuPar le compte dont on veut les encaissements, ou {@code null} pour tous */
    public ReglementsDettesResponse historique(LocalDate debut, LocalDate fin, String recuPar) {
        return construire(debut, fin, charger(debut, fin, recuPar, REGLEMENTS_ECRAN_MAX, "l'afficher"));
    }

    /** Les règlements de la période, les plus récents d'abord. */
    public List<ReglementClient> charger(LocalDate debut, LocalDate fin, String recuPar, int limite, String usage) {
        HistoriqueVentesService.verifierPeriode(debut, fin);
        var du = debut.atStartOfDay();
        var au = fin.plusDays(1).atStartOfDay();
        long total = reglementRepository.compterReglementsDeLaPeriode(du, au, recuPar);
        if (total > limite) {
            throw new RequeteInvalideException("Cette période compte " + total + " règlements : c'est trop pour " + usage
                + " (" + limite + " au plus). Choisissez une période plus courte.");
        }
        return reglementRepository.reglementsDeLaPeriode(du, au, recuPar);
    }

    public ReglementsDettesResponse construire(LocalDate debut, LocalDate fin, List<ReglementClient> reglements) {
        Map<Long, BigDecimal> resteApres = resteApresChaqueReglement(reglements);
        List<LigneReglementDette> lignes = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        long dettesSoldees = 0;
        Map<ModeReglement, BigDecimal> montants = new EnumMap<>(ModeReglement.class);
        Map<ModeReglement, Long> nombres = new EnumMap<>(ModeReglement.class);
        for (ReglementClient reglement : reglements) {
            DetteClient dette = reglement.getDette();
            boolean annulee = dette.getStatut() == StatutDette.ANNULEE;
            BigDecimal reste = annulee ? null : resteApres.get(reglement.getId());
            if (reste != null && reste.signum() == 0) {
                dettesSoldees++;
            }
            total = total.add(reglement.getMontant());
            montants.merge(reglement.getModeReglement(), reglement.getMontant(), BigDecimal::add);
            nombres.merge(reglement.getModeReglement(), 1L, Long::sum);
            lignes.add(new LigneReglementDette(
                reglement.getId(),
                reglement.getDateReglement(),
                reglement.getClient().getNom(),
                dette.getVente() == null ? null : dette.getVente().getCode(),
                reglement.getMontant(),
                reglement.getModeReglement(),
                reglement.getReference(),
                reglement.getRecuPar() == null ? null : reglement.getRecuPar().getUsername(),
                reste,
                annulee));
        }
        List<TotalParMode> parMode = montants.entrySet().stream()
            .map(entree -> new TotalParMode(entree.getKey(), nombres.get(entree.getKey()), entree.getValue()))
            .toList();
        return new ReglementsDettesResponse(debut, fin, new ResumeReglementsDettes(lignes.size(), total, dettesSoldees, parMode), lignes);
    }

    /**
     * Ce qui restait dû sur sa dette juste après chaque règlement : on reprend TOUS les règlements de ces dettes (même ceux d'avant
     * la période), dans l'ordre où ils ont été reçus.
     */
    private Map<Long, BigDecimal> resteApresChaqueReglement(List<ReglementClient> reglements) {
        Set<Long> dettes = reglements.stream().map(reglement -> reglement.getDette().getId()).collect(Collectors.toSet());
        Map<Long, BigDecimal> resteApres = new HashMap<>();
        Map<Long, BigDecimal> payeParDette = new HashMap<>();
        for (List<Long> paquet : HistoriqueVentesService.paquets(dettes)) {
            for (ReglementClient reglement : reglementRepository.findByDetteIdInOrderByDateReglementAscIdAsc(paquet)) {
                DetteClient dette = reglement.getDette();
                BigDecimal paye = payeParDette.merge(dette.getId(), reglement.getMontant(), BigDecimal::add);
                resteApres.put(reglement.getId(), dette.getMontantInitial().subtract(paye).max(BigDecimal.ZERO));
            }
        }
        return resteApres;
    }
}
