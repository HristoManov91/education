package bg.hristomanov.education.hexagonal.port.in;

public interface GetOrderUseCase {

    OrderView get(long orderId);
}
