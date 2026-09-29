package bg.hristomanov.education.specification.service;

import bg.hristomanov.education.specification.domain.PaymentEntity;
import bg.hristomanov.education.specification.repository.NaivePaymentCriteriaRepository;
import bg.hristomanov.education.specification.repository.PaymentQuerydslRepository;
import bg.hristomanov.education.specification.repository.PaymentRepository;
import bg.hristomanov.education.specification.search.PaymentSearchCriteria;
import bg.hristomanov.education.specification.search.PaymentSpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Side-by-side вход към трите варианта, за да сравняваме semantics,
 * а не само syntax.
 */
@Service
@Transactional(readOnly = true)
public class PaymentSearchService {

    private static final Sort SEARCH_ORDER = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.asc("id")
    );

    private final NaivePaymentCriteriaRepository naiveRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentQuerydslRepository querydslRepository;

    public PaymentSearchService(
            NaivePaymentCriteriaRepository naiveRepository,
            PaymentRepository paymentRepository,
            PaymentQuerydslRepository querydslRepository
    ) {
        this.naiveRepository = naiveRepository;
        this.paymentRepository = paymentRepository;
        this.querydslRepository = querydslRepository;
    }

    public List<PaymentEntity> searchWithNaiveCriteria(PaymentSearchCriteria criteria) {
        return naiveRepository.search(criteria);
    }

    public List<PaymentEntity> searchWithSpecifications(PaymentSearchCriteria criteria) {
        return paymentRepository.findAll(
                PaymentSpecifications.matches(criteria),
                SEARCH_ORDER
        );
    }

    public List<PaymentEntity> searchWithQuerydsl(PaymentSearchCriteria criteria) {
        return querydslRepository.search(criteria);
    }
}
