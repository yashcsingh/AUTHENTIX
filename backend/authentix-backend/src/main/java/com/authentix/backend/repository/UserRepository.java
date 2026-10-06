package com.authentix.backend.repository;

import com.authentix.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserCode(String userCode);

    Optional<User> findByEmail(String email);

    Optional<User> findByQrCodeValue(String qrCodeValue);

    boolean existsByEmail(String email);

    boolean existsByUserCode(String userCode);

    Optional<User> findByWalletAddress(String walletAddress);

    Optional<User> findByWalletAddressIgnoreCase(String walletAddress);

    boolean existsByWalletAddress(String walletAddress);

    boolean existsByWalletAddressIgnoreCase(String walletAddress);
}