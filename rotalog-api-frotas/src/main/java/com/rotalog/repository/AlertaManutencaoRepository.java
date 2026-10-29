package com.rotalog.repository;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.StatusAlerta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AlertaManutencaoRepository extends JpaRepository<AlertaManutencao, Long> {

    List<AlertaManutencao> findAllByOrderByDataCriacaoDesc();

    List<AlertaManutencao> findByStatusOrderByDataCriacaoDesc(StatusAlerta status);

    List<AlertaManutencao> findByStatus(StatusAlerta status);

    boolean existsByVeiculoIdAndStatusIn(Long veiculoId, Collection<StatusAlerta> status);
}
