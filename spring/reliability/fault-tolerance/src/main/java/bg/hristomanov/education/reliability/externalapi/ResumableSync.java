package bg.hristomanov.education.reliability.externalapi;

import java.util.List;
import java.util.Objects;

/**
 * Worker-ът не запазва progress в паметта си: чете durable checkpoint преди
 * всяка страница. Ако HTTP fetch хвърли 429/timeout, текущата страница не се
 * маркира като готова, а следващ run започва от същия page.
 */
public final class ResumableSync {

    public record Item(String externalId, String content) {
        public Item {
            Objects.requireNonNull(externalId, "externalId");
            Objects.requireNonNull(content, "content");
        }
    }

    public record Page(List<Item> items, boolean hasMore) {
        public Page {
            items = List.copyOf(items);
        }
    }

    public record Progress(int nextPage, boolean complete) {
        public Progress {
            if (nextPage < 1) {
                throw new IllegalArgumentException("Page numbers start from 1");
            }
        }
    }

    @FunctionalInterface
    public interface PageFetcher {
        Page fetch(int pageNumber);
    }

    public interface PageStore {
        Progress progress(String jobId);
        void commitPage(String jobId, int expectedPage, Page page);
    }

    private final PageStore store;

    public ResumableSync(PageStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public void run(String jobId, PageFetcher fetcher) {
        Objects.requireNonNull(fetcher, "fetcher");
        while (true) {
            Progress progress = store.progress(jobId);
            if (progress.complete()) {
                return;
            }
            // Важното: НИКАКЪВ checkpoint преди успешно fetch + atomic persist.
            Page page = Objects.requireNonNull(fetcher.fetch(progress.nextPage()), "page");
            store.commitPage(jobId, progress.nextPage(), page);
        }
    }
}
