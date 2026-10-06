package com.authentix.backend.repository;

import com.authentix.backend.entity.WalletVerificationChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletVerificationChallengeRepository extends JpaRepository<WalletVerificationChallenge, Long> {

    Optional<WalletVerificationChallenge> findByChallenge(String challenge);
}
