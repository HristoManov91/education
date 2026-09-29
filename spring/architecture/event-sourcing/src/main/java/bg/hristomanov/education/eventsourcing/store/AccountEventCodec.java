package bg.hristomanov.education.eventsourcing.store;

import bg.hristomanov.education.eventsourcing.domain.*;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AccountEventCodec {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final JsonMapper jsonMapper;

    public AccountEventCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String eventType(AccountEvent event) {
        return switch (event) {
            case AccountOpened ignored -> "AccountOpened";
            case MoneyDeposited ignored -> "MoneyDeposited";
            case MoneyWithdrawn ignored -> "MoneyWithdrawn";
        };
    }

    public String serialize(AccountEvent event) {
        try {
            return jsonMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Cannot serialize account event",
                    exception
            );
        }
    }

    public AccountEvent deserialize(
            String eventType,
            int schemaVersion,
            String payload
    ) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException(
                    "Unsupported event schema version "
                            + schemaVersion
                            + " for "
                            + eventType
            );
        }

        try {
            return switch (eventType) {
                case "AccountOpened" ->
                        jsonMapper.readValue(
                                payload,
                                AccountOpened.class
                        );
                case "MoneyDeposited" ->
                        jsonMapper.readValue(
                                payload,
                                MoneyDeposited.class
                        );
                case "MoneyWithdrawn" ->
                        jsonMapper.readValue(
                                payload,
                                MoneyWithdrawn.class
                        );
                default ->
                        throw new IllegalArgumentException(
                                "Unknown event type: " + eventType
                        );
            };
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Cannot deserialize account event "
                            + eventType,
                    exception
            );
        }
    }
}
