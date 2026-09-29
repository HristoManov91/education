package bg.hristomanov.education.patterns.behavioral.visitor;

public interface PaymentElement {

    <R> R accept(PaymentVisitor<R> visitor);
}
