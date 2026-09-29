package bg.hristomanov.education.patterns.behavioral.iterator;

public interface OrderPageSource {

    OrderPage fetchPage(int pageNumber, int pageSize);
}
