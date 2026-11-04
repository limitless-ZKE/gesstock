package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.MouvementCaisse;
import com.gesstock.gesstock.entities.OrigineMouvementCaisse;
import com.gesstock.gesstock.entities.TypeMouvementCaisse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface MouvementCaisseRepository extends JpaRepository<MouvementCaisse, Long> {
    List<MouvementCaisse> findBySessionCaisseIdOrderByDateMouvementDesc(Long sessionId);

    /** Espèces sorties de la caisse pendant la session, quelle qu'en soit l'origine. */
    @Query("select coalesce(sum(m.montant), 0) from MouvementCaisse m "
        + "where m.sessionCaisse.id = :sessionId and m.type = com.gesstock.gesstock.entities.TypeMouvementCaisse.SORTIE")
    BigDecimal totalSorties(@Param("sessionId") Long sessionId);

    /** Espèces ajoutées à la caisse pendant la session (apports de fonds). */
    @Query("select coalesce(sum(m.montant), 0) from MouvementCaisse m "
        + "where m.sessionCaisse.id = :sessionId and m.type = com.gesstock.gesstock.entities.TypeMouvementCaisse.ENTREE")
    BigDecimal totalEntrees(@Param("sessionId") Long sessionId);

    /** Total d'un sens de mouvement (et d'une origine) sur une période : [debut, fin[. */
    @Query("select coalesce(sum(m.montant), 0) from MouvementCaisse m where m.type = :type and m.origine = :origine "
        + "and m.dateMouvement >= :debut and m.dateMouvement < :fin")
    BigDecimal totalEntre(@Param("type") TypeMouvementCaisse type, @Param("origine") OrigineMouvementCaisse origine,
                          @Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    /** Les mouvements d'un sens et d'une origine sur une période, du plus ancien au plus récent. */
    @Query("select m from MouvementCaisse m join fetch m.effectuePar where m.type = :type and m.origine = :origine "
        + "and m.dateMouvement >= :debut and m.dateMouvement < :fin order by m.dateMouvement asc, m.id asc")
    List<MouvementCaisse> entre(@Param("type") TypeMouvementCaisse type, @Param("origine") OrigineMouvementCaisse origine,
                                @Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);
}
