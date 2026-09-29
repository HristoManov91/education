package bg.hristomanov.education.cache.controller;

import bg.hristomanov.education.cache.domain.CacheStatistics;
import bg.hristomanov.education.cache.domain.LabState;
import bg.hristomanov.education.cache.domain.Product;
import bg.hristomanov.education.cache.domain.UpdateProductRequest;
import bg.hristomanov.education.cache.repository.ProductRepository;
import bg.hristomanov.education.cache.service.CachingStrategyService;
import bg.hristomanov.education.cache.service.RefreshAheadProductService;
import bg.hristomanov.education.cache.service.WriteBehindProductService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP boundary за сравняване на еднакви requests през различни cache policies.
 */
@RestController
@RequestMapping("/api/cache-lab")
public class CachingLabController {

    private final ProductRepository repository;
    private final WriteBehindProductService writeBehindProductService;
    private final RefreshAheadProductService refreshAheadProductService;
    private final Map<String, CachingStrategyService> strategies;

    public CachingLabController(
            ProductRepository repository,
            WriteBehindProductService writeBehindProductService,
            RefreshAheadProductService refreshAheadProductService,
            List<CachingStrategyService> strategyServices
    ) {
        this.repository = repository;
        this.writeBehindProductService = writeBehindProductService;
        this.refreshAheadProductService = refreshAheadProductService;

        Map<String, CachingStrategyService> strategyMap = new LinkedHashMap<>();
        for (CachingStrategyService strategyService : strategyServices) {
            strategyMap.put(strategyService.strategyName(), strategyService);
        }
        this.strategies = Map.copyOf(strategyMap);
    }

    @GetMapping("/{strategy}/products/{productId}")
    public Product getProduct(
            @PathVariable String strategy,
            @PathVariable long productId
    ) {
        return strategy(strategy).get(productId);
    }

    @PutMapping("/{strategy}/products/{productId}")
    public Product updateProduct(
            @PathVariable String strategy,
            @PathVariable long productId,
            @RequestBody UpdateProductRequest request
    ) {
        return strategy(strategy).update(productId, request.name(), request.price());
    }

    @GetMapping("/{strategy}/stats")
    public CacheStatistics cacheStatistics(@PathVariable String strategy) {
        return strategy(strategy).cacheStatistics();
    }

    @GetMapping("/state/{productId}")
    public LabState state(@PathVariable long productId) {
        Map<String, CacheStatistics> cacheStatistics = new LinkedHashMap<>();
        for (Map.Entry<String, CachingStrategyService> entry : strategies.entrySet()) {
            cacheStatistics.put(entry.getKey(), entry.getValue().cacheStatistics());
        }

        return new LabState(
                repository.peek(productId),
                repository.readCount(),
                repository.writeCount(),
                writeBehindProductService.pendingWriteCount(),
                Map.copyOf(cacheStatistics)
        );
    }

    @PostMapping("/reset")
    public LabState reset() {
        for (CachingStrategyService strategyService : strategies.values()) {
            strategyService.clearCache();
        }
        repository.reset();
        return state(1L);
    }

    @PostMapping("/write-behind/flush")
    public Map<String, Integer> flushWriteBehind() {
        int flushed = writeBehindProductService.flushPendingWrites();
        return Map.of("flushed", flushed);
    }

    @PostMapping("/refresh-ahead/refresh")
    public Map<String, Integer> refreshAhead() {
        int refreshed = refreshAheadProductService.refreshHotEntries();
        return Map.of("refreshed", refreshed);
    }

    @GetMapping("/strategies")
    public List<String> strategies() {
        return strategies.keySet().stream().sorted().toList();
    }

    private CachingStrategyService strategy(String strategyName) {
        CachingStrategyService strategy = strategies.get(strategyName);
        if (strategy == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown strategy '" + strategyName + "'. Available: " + strategies.keySet()
            );
        }
        return strategy;
    }
}
