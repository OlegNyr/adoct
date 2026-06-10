package ru.gitverse.adoct.client;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ru.gitverse.adoct.client.content.ContentMainPage;

import java.text.SimpleDateFormat;

public interface ObjectMapperExt {
    ObjectMapper INSTANT = new ObjectMapper()
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
                    new DeserializationFeature[]{DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT,
                            DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT,
                            DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL,
                            DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS})
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                    new DeserializationFeature[]{DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES,
                            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                            DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS,
                            DeserializationFeature.WRAP_EXCEPTIONS})
            .enable(new JsonParser.Feature[]{JsonParser.Feature.ALLOW_SINGLE_QUOTES})
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ"))
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

}
