package bg.hristomanov.education.containers.customer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CustomerDirectory {

    private final CustomerRepository customerRepository;

    public CustomerDirectory(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer register(String email, String displayName) {
        return customerRepository.save(new Customer(email, displayName));
    }

    @Transactional(readOnly = true)
    public Optional<String> findDisplayNameByEmail(String email) {
        return customerRepository.findByEmail(email).map(Customer::getDisplayName);
    }

    @Transactional
    public void deleteAll() {
        customerRepository.deleteAll();
    }
}
