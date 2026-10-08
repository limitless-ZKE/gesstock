package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.HistoriquePrixAchat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoriquePrixAchatRepository extends JpaRepository<HistoriquePrixAchat, Long> {

    List<HistoriquePrixAchat> findByArticleIdOrderByDateEffetDescIdDesc(Long articleId);
}
