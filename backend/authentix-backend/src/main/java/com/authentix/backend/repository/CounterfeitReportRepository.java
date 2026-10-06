package com.authentix.backend.repository;

import com.authentix.backend.entity.CounterfeitReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CounterfeitReportRepository
        extends JpaRepository<CounterfeitReport, Long> {

    List<CounterfeitReport> findByAssetIdOrderByReportedAtDesc(Long assetId);

    List<CounterfeitReport> findByReporterIdOrderByReportedAtDesc(Long reporterId);

    List<CounterfeitReport> findByStatusOrderByReportedAtDesc(String status);
}