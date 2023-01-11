package io.tapdata.connector.postgres.converters;

import io.debezium.spi.converter.CustomConverter;
import io.debezium.spi.converter.RelationalColumn;
import io.tapdata.kit.EmptyKit;
import org.apache.kafka.connect.data.SchemaBuilder;

import java.time.Duration;
import java.util.Properties;

public class TimeConverter implements CustomConverter<SchemaBuilder, RelationalColumn> {

    private SchemaBuilder timeSchema;

    @Override
    public void configure(Properties props) {
        timeSchema = SchemaBuilder.int64().name(props.getProperty("schema.name"));
    }

    @Override
    public void converterFor(RelationalColumn column,
                             ConverterRegistration<SchemaBuilder> registration) {

        if ("time".equals(column.typeName())) {
            registration.register(timeSchema, x -> {
                if (EmptyKit.isNull(x)) {
                    return null;
                }
                Duration duration = (Duration) x;
                return duration.getSeconds() * 1000000 + duration.getNano() / 1000;
            });
        }
    }
}