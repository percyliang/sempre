package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.*;
import java.util.Map;

/**
 * Simple wrappers and sane defaults for Jackson.
 *
 * @author Roy Frostig
 */
public final class Json {
  private Json() { }

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  static {
    OBJECT_MAPPER.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
  }

  public static ObjectMapper getMapper() {
    return OBJECT_MAPPER;
  }

  private static ObjectWriter getWriter(Class<?> view) {
    if (view != null)
      return getMapper().writerWithView(view);
    else
      return getMapper().writer();
  }

  private static ObjectReader getReader(Class<?> view) {
    if (view != null)
      return getMapper().readerWithView(view);
    else
      return getMapper().reader();
  }

  // TODO (rf):
  // - readValueHard from InputStream, Reader, JsonParser, and File
  //   (all forwards)

  public static <T> T readValueHard(String json, Class<T> klass) {
    return readValueHard(json, klass, Object.class);
  }
  public static <T> T readValueHard(String json, Class<T> klass, Class<?> view) {
    try {
      return getReader(view).withType(klass).readValue(json);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T readValueHard(String json, TypeReference<T> typeRef) {
    return readValueHard(json, typeRef, Object.class);
  }
  public static <T> T readValueHard(String json, TypeReference<T> typeRef, Class<?> view) {
    try {
      return getReader(view).withType(typeRef).readValue(json);
    } catch (JsonMappingException e) {
      throw new RuntimeException(e);
    } catch (JsonParseException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T readValueHard(Reader r, Class<T> klass) {
    return readValueHard(r, klass, Object.class);
  }
  public static <T> T readValueHard(Reader r, Class<T> klass, Class<?> view) {
    try {
      return getReader(view).withType(klass).readValue(r);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T readValueHard(Reader r, TypeReference<T> typeRef) {
    return readValueHard(r, typeRef, Object.class);
  }
  public static <T> T readValueHard(Reader r, TypeReference<T> typeRef, Class<?> view) {
    try {
      return getReader(view).withType(typeRef).readValue(r);
    } catch (JsonMappingException e) {
      throw new RuntimeException(e);
    } catch (JsonParseException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<String, Object> readMapHard(String json) {
    return readMapHard(json, Object.class);
  }
  public static Map<String, Object> readMapHard(String json, Class<?> view) {
    try {
      return getReader(view).withType(Map.class).readValue(json);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String prettyWriteValueAsStringHard(Object o) {
    try {
      return getMapper().writerWithDefaultPrettyPrinter().writeValueAsString(o);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
  public static String writeValueAsStringHard(Object o) {
    return writeValueAsStringHard(o, Object.class);
  }
  public static String writeValueAsStringHard(Object o, Class<?> view) {
    try {
      return getWriter(view).writeValueAsString(o);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static byte[] writeValueAsBytesHard(Object o) {
    return writeValueAsBytesHard(o, Object.class);
  }
  public static byte[] writeValueAsBytesHard(Object o, Class<?> view) {
    try {
      return getWriter(view).writeValueAsBytes(o);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static void prettyWriteValueHard(File f, Object o) {
    try {
      getMapper().writerWithDefaultPrettyPrinter().writeValue(f, o);
    } catch (JsonMappingException e) {
      e.printStackTrace();
    } catch (JsonGenerationException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  public static void writeValueHard(File f, Object o) {
    writeValueHard(f, o, Object.class);
  }
  public static void writeValueHard(File f, Object o, Class<?> view) {
    try {
      getWriter(view).writeValue(f, o);
    } catch (JsonMappingException e) {
      e.printStackTrace();
    } catch (JsonGenerationException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void writeValueHard(OutputStream out, Object o) {
    writeValueHard(out, o, Object.class);
  }
  public static void writeValueHard(OutputStream out, Object o, Class<?> view) {
    try {
      getWriter(view).writeValue(out, o);
    } catch (JsonMappingException e) {
      throw new RuntimeException(e);
    } catch (JsonGenerationException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void writeValueHard(JsonGenerator jg, Object o) {
    writeValueHard(jg, o, Object.class);
  }
  public static void writeValueHard(JsonGenerator jg, Object o, Class<?> view) {
    try {
      getWriter(view).writeValue(jg, o);
    } catch (JsonMappingException e) {
      throw new RuntimeException(e);
    } catch (JsonGenerationException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void writeValueHard(Writer w, Object o) {
    writeValueHard(w, o, Object.class);
  }
  public static void writeValueHard(Writer w, Object o, Class<?> view) {
    try {
      getWriter(view).writeValue(w, o);
    } catch (JsonMappingException e) {
      throw new RuntimeException(e);
    } catch (JsonGenerationException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
