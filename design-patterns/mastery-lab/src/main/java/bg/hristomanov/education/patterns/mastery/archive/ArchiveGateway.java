package bg.hristomanov.education.patterns.mastery.archive;

@FunctionalInterface
public interface ArchiveGateway {
    String archive(String reference, String content);
}
