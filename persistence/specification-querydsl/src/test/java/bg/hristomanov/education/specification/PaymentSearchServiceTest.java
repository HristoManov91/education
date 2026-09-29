package bg.hristomanov.education.specification;

import bg.hristomanov.education.specification.domain.PaymentEntity;
import bg.hristomanov.education.specification.domain.PaymentMethod;
import bg.hristomanov.education.specification.domain.PaymentStatus;
import bg.hristomanov.education.specification.repository.PaymentRepository;
import bg.hristomanov.education.specification.search.PaymentSearchCriteria;
import bg.hristomanov.education.specification.search.PaymentSpecifications;
import bg.hristomanov.education.specification.service.PaymentSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PaymentSearchServiceTest {

    private final PaymentSearchService searchService;
    private final PaymentRepository paymentRepository;

    @Autowired
    PaymentSearchServiceTest(
            PaymentSearchService searchService,
            PaymentRepository paymentRepository
    ) {
        this.searchService = searchService;
        this.paymentRepository = paymentRepository;
    }

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();

        paymentRepository.saveAll(
                List.of(
                        payment(
                                "PAY-BG-001",
                                PaymentStatus.PAID,
                                PaymentMethod.CARD,
                                "BG",
                                "120.00",
                                "2026-09-01T10:00:00"
                        ),
                        payment(
                                "PAY-BG-002",
                                PaymentStatus.PAID,
                                PaymentMethod.BANK_TRANSFER,
                                "BG",
                                "80.00",
                                "2026-09-02T10:00:00"
                        ),
                        payment(
                                "PAY-BG-003",
                                PaymentStatus.FAILED,
                                PaymentMethod.CARD,
                                "BG",
                                "300.00",
                                "2026-09-03T10:00:00"
                        ),
                        payment(
                                "PAY-DE-004",
                                PaymentStatus.PAID,
                                PaymentMethod.CARD,
                                "DE",
                                "500.00",
                                "2026-09-04T10:00:00"
                        ),
                        payment(
                                "INV-SPECIAL-005",
                                PaymentStatus.PAID,
                                PaymentMethod.CARD,
                                "BG",
                                "250.00",
                                "2026-09-05T10:00:00"
                        )
                )
        );
        paymentRepository.flush();
    }

    @Test
    void naiveSpecificationsAndQuerydslReturnTheSameBusinessResult() {
        PaymentSearchCriteria criteria = new PaymentSearchCriteria(
                PaymentStatus.PAID,
                PaymentMethod.CARD,
                "bg",
                new BigDecimal("100.00"),
                LocalDateTime.parse("2026-09-01T00:00:00"),
                LocalDateTime.parse("2026-09-30T23:59:59"),
                null
        );

        List<String> naiveReferences =
                references(searchService.searchWithNaiveCriteria(criteria));
        List<String> specificationReferences =
                references(searchService.searchWithSpecifications(criteria));
        List<String> querydslReferences =
                references(searchService.searchWithQuerydsl(criteria));

        assertThat(naiveReferences)
                .containsExactly("INV-SPECIAL-005", "PAY-BG-001");
        assertThat(specificationReferences).isEqualTo(naiveReferences);
        assertThat(querydslReferences).isEqualTo(naiveReferences);
    }

    @Test
    void optionalFiltersCanBeAddedWithoutCreatingRepositoryMethodCombinations() {
        PaymentSearchCriteria criteria = new PaymentSearchCriteria(
                null,
                null,
                null,
                null,
                null,
                null,
                "special"
        );

        assertThat(references(searchService.searchWithSpecifications(criteria)))
                .containsExactly("INV-SPECIAL-005");

        assertThat(references(searchService.searchWithQuerydsl(criteria)))
                .containsExactly("INV-SPECIAL-005");
    }

    @Test
    void individualSpecificationsRemainReusableOutsideTheMainSearchBuilder() {
        List<PaymentEntity> results = paymentRepository.findAll(
                PaymentSpecifications
                        .hasStatus(PaymentStatus.PAID)
                        .and(PaymentSpecifications.amountAtLeast(new BigDecimal("200.00"))),
                Sort.by(Sort.Order.desc("amount"))
        );

        assertThat(references(results))
                .containsExactly("PAY-DE-004", "INV-SPECIAL-005");
    }

    @Test
    void emptyCriteriaMeansNoRestrictionInsteadOfASecondFindAllCodePath() {
        List<String> specificationReferences =
                references(searchService.searchWithSpecifications(PaymentSearchCriteria.empty()));
        List<String> querydslReferences =
                references(searchService.searchWithQuerydsl(PaymentSearchCriteria.empty()));

        assertThat(specificationReferences).hasSize(5);
        assertThat(querydslReferences).isEqualTo(specificationReferences);
    }

    private PaymentEntity payment(
            String reference,
            PaymentStatus status,
            PaymentMethod method,
            String countryCode,
            String amount,
            String createdAt
    ) {
        return new PaymentEntity(
                reference,
                status,
                method,
                countryCode,
                new BigDecimal(amount),
                LocalDateTime.parse(createdAt)
        );
    }

    private List<String> references(List<PaymentEntity> payments) {
        return payments.stream()
                .map(PaymentEntity::getReference)
                .toList();
    }
}
