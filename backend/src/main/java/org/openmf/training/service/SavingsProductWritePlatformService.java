package org.openmf.training.service;

import org.openmf.training.data.CreateSavingsProductRequest;
import org.openmf.training.data.SavingsProductData;
import org.openmf.training.domain.SavingsProduct;
import org.openmf.training.repository.SavingsProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SavingsProductWritePlatformService {

    private final SavingsProductRepository productRepository;

    public SavingsProductWritePlatformService(
            SavingsProductRepository productRepository
    ) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<SavingsProductData> retrieveAll() {
        return productRepository.findAll()
                .stream()
                .map(this::toData)
                .toList();
    }

    @Transactional(readOnly = true)
    public SavingsProductData retrieveOne(Long id) {
        SavingsProduct product = productRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Savings product not found: " + id)
                );

        return toData(product);
    }

    @Transactional
    public SavingsProductData create(CreateSavingsProductRequest request) {

        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }

        if (request.nominalAnnualInterestRate() == null) {
            throw new IllegalArgumentException(
                    "Nominal annual interest rate is required"
            );
        }
        validateClosureFee(request.closureFee());

        SavingsProduct product = new SavingsProduct();
        product.setName(request.name());
        product.setNominalAnnualInterestRate(request.nominalAnnualInterestRate());
        product.setMinRequiredBalance(
                request.minRequiredBalance() != null ? request.minRequiredBalance() : BigDecimal.ZERO
        );
        product.setWithdrawalFee(
                request.withdrawalFee() != null ? request.withdrawalFee() : BigDecimal.ZERO
        );
        product.setClosureFee(
                request.closureFee() != null ? request.closureFee() : BigDecimal.ZERO
        );
        product.setCreatedOn(LocalDateTime.now());

        return toData(productRepository.save(product));
    }

    @Transactional
    public SavingsProductData update(
            Long id,
            CreateSavingsProductRequest request
    ) {
        validateClosureFee(request.closureFee());

        SavingsProduct product = productRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Savings product not found: " + id)
                );

        product.setName(request.name());
        product.setNominalAnnualInterestRate(
                request.nominalAnnualInterestRate()
        );
        if (request.minRequiredBalance() != null) {
            product.setMinRequiredBalance(request.minRequiredBalance());
        }
        if (request.withdrawalFee() != null) {
            product.setWithdrawalFee(request.withdrawalFee());
        }
        if (request.closureFee() != null) {
            product.setClosureFee(request.closureFee());
        }

        return toData(productRepository.save(product));
    }

    private SavingsProductData toData(SavingsProduct product) {
        return new SavingsProductData(
                product.getId(),
                product.getName(),
                product.getNominalAnnualInterestRate(),
                product.getMinRequiredBalance(),
                product.getWithdrawalFee(),
                product.getClosureFee(),
                product.getCreatedOn()
        );
    }

    private void validateClosureFee(BigDecimal closureFee) {
        if (closureFee != null && closureFee.signum() < 0) {
            throw new IllegalArgumentException("Closure fee cannot be negative");
        }
    }
}