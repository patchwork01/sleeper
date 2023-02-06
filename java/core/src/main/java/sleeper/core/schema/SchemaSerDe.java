/*
 * Copyright 2022-2023 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sleeper.core.schema;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.stream.JsonReader;
import org.apache.commons.codec.binary.Base64;

import sleeper.core.schema.type.ByteArrayType;
import sleeper.core.schema.type.IntType;
import sleeper.core.schema.type.ListType;
import sleeper.core.schema.type.LongType;
import sleeper.core.schema.type.MapType;
import sleeper.core.schema.type.PrimitiveType;
import sleeper.core.schema.type.StringType;
import sleeper.core.schema.type.Type;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * Serialises a {@link Schema} to and from a JSON {@link String}.
 */
public class SchemaSerDe {
    private final Gson gson;
    private final Gson gsonPrettyPrinting;

    public SchemaSerDe() {
        try {
            this.gson = new GsonBuilder()
                    .registerTypeAdapter(Class.forName(Type.class.getName()), new AbstractTypeJsonSerializer())
                    .registerTypeAdapter(Class.forName(Type.class.getName()), new AbstractTypeJsonDeserializer())
                    .create();
            this.gsonPrettyPrinting = new GsonBuilder()
                    .setPrettyPrinting()
                    .registerTypeAdapter(Class.forName(Type.class.getName()), new AbstractTypeJsonSerializer())
                    .registerTypeAdapter(Class.forName(Type.class.getName()), new AbstractTypeJsonDeserializer())
                    .create();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Exception creating Gson", e);
        }
    }

    public String toJson(Schema schema) {
        return gson.toJson(schema);
    }

    public String toJson(Schema schema, boolean prettyPrint) {
        if (prettyPrint) {
            return gsonPrettyPrinting.toJson(schema);
        }
        return toJson(schema);
    }

    public Schema fromJson(String jsonSchema) {
        return gson.fromJson(jsonSchema, Schema.Builder.class).build();
    }

    public Schema fromJson(InputStream inputStream) {
        return gson.fromJson(new InputStreamReader(inputStream, Charset.forName("UTF-8")), Schema.class);
    }

    public String toBase64EncodedJson(Schema schema) {
        byte[] bytes = toJson(schema).getBytes(Charset.forName("UTF-8"));
        return Base64.encodeBase64String(bytes);
    }

    public Schema fromBase64EncodedJson(String encodedSchema) {
        byte[] bytes = Base64.decodeBase64(encodedSchema);
        String jsonSchema = new String(bytes, Charset.forName("UTF-8"));
        return fromJson(jsonSchema);
    }

    public Schema fromJsonFile(String jsonFile) throws FileNotFoundException {
        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(jsonFile), Charset.forName("UTF-8")));
        return gson.fromJson(reader, Schema.class);
    }

    public static class AbstractTypeJsonSerializer implements JsonSerializer<Type> {

        @Override
        public JsonElement serialize(Type type, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            if (type instanceof IntType) {
                return new JsonPrimitive("IntType");
            }
            if (type instanceof LongType) {
                return new JsonPrimitive("LongType");
            }
            if (type instanceof StringType) {
                return new JsonPrimitive("StringType");
            }
            if (type instanceof ByteArrayType) {
                return new JsonPrimitive("ByteArrayType");
            }
            if (type instanceof MapType) {
                MapType mapType = (MapType) type;
                PrimitiveType keyType = mapType.getKeyType();
                PrimitiveType valueType = mapType.getValueType();
                JsonObject object2 = new JsonObject();
                object2.addProperty("keyType", keyType.getClass().getSimpleName());
                object2.addProperty("valueType", valueType.getClass().getSimpleName());
                JsonObject object = new JsonObject();
                object.add("MapType", object2);
                return object;
            }
            if (type instanceof ListType) {
                ListType listType = (ListType) type;
                PrimitiveType elementType = listType.getElementType();
                JsonObject object2 = new JsonObject();
                object2.addProperty("elementType", elementType.getClass().getSimpleName());
                JsonObject object = new JsonObject();
                object.add("ListType", object2);
                return object;
            }
            throw new IllegalArgumentException("Unknown type " + type);
        }
    }

    public static class AbstractTypeJsonDeserializer implements JsonDeserializer<Type> {

        @Override
        public Type deserialize(JsonElement jsonElement, java.lang.reflect.Type typeOfSrc, JsonDeserializationContext context) throws JsonParseException {
            if (jsonElement.isJsonPrimitive()) {
                String primitive = jsonElement.getAsJsonPrimitive().getAsString();
                return getPrimitiveTypeFromString(primitive);
            }
            if (jsonElement.isJsonObject()) {
                JsonObject object = jsonElement.getAsJsonObject();
                if (object.has("MapType")) {
                    JsonObject object2 = object.get("MapType").getAsJsonObject();
                    String keyType = object2.get("keyType").getAsString();
                    String valueType = object2.get("valueType").getAsString();
                    return new MapType(getPrimitiveTypeFromString(keyType), getPrimitiveTypeFromString(valueType));
                }
                if (object.has("ListType")) {
                    JsonObject object2 = object.get("ListType").getAsJsonObject();
                    String elementType = object2.get("elementType").getAsString();
                    return new ListType(getPrimitiveTypeFromString(elementType));
                }
            }
            throw new IllegalArgumentException("Unknown type " + jsonElement);
        }
    }

    private static PrimitiveType getPrimitiveTypeFromString(String primitiveTypeString) {
        if (primitiveTypeString.equals("IntType")) {
            return new IntType();
        }
        if (primitiveTypeString.equals("LongType")) {
            return new LongType();
        }
        if (primitiveTypeString.equals("StringType")) {
            return new StringType();
        }
        if (primitiveTypeString.equals("ByteArrayType")) {
            return new ByteArrayType();
        }
        throw new RuntimeException("Unknown type " + primitiveTypeString);
    }
}
