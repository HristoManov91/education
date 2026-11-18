package bg.hristomanov.education.uow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** A deliberate anti-example of transactional self-invocation with Spring proxy AOP. */
@Service
public class VladSelfInvocationProbe {

    public boolean internalCallBypassesTransactionalProxy() {
        return inspectedTransactionStatus(); // this.method: does not cross the Spring proxy.
    }

    @Transactional
    public boolean inspectedTransactionStatus() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }
}
