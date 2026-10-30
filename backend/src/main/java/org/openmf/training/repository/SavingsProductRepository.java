package org.openmf.training.repository;

import org.openmf.training.domain.SavingsProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface SavingsProductRepository
        extends JpaRepository<SavingsProduct, Long> {

    @Query(value = "select closure_fee from m_savings_product where id = :productId", nativeQuery = true)
    Optional<BigDecimal> findClosureFeeById(@Param("productId") Long productId);
}